-- 환불 금액 계산 — Paddle 대시보드에서 환불하기 "직전"에 운영 DB 에서 실행합니다.
--
-- 처리 절차
--   1. 사용자가 메일로 환불 요청 → 가입 이메일로 user_id 확인 (SELECT user_id FROM users WHERE email = '...')
--   2. 아래 @user_id 를 바꿔 실행 → 결제마다 refund_amount 가 나옵니다 (환불 정책: 처리 시점 기준)
--   3. Paddle 대시보드 > Transactions > paddle_transaction_id 검색 > Refund 에 refund_amount 입력
--      (refund_amount = amount 면 전액, 0 이면 그 결제는 환불하지 않음)
--      refund_amount 가 NULL 이면 결제 금액을 못 읽어 0 으로 저장된 결제입니다. Paddle 대시보드에서 그 거래의 금액을 보고
--      (left_credits − used) × 금액 ÷ credits 로 직접 계산하세요.
--   4. Paddle 이 승인하면 웹훅(adjustment.updated)이 와서 서버가 그만큼 크레딧을 자동으로 뺍니다. DB 를 직접 고치지 마세요.
--
-- 계산 (환불 및 청약철회 정책 제1조)
--   - 무료 크레딧을 먼저, 그다음 먼저 산 결제부터 쓴 것으로 봅니다.
--   - 유료로 쓴 횟수 = 아직 환불 안 한 유료 충전 합계 − 지금 남은 크레딧 (0 보다 작으면 0: 무료도 아직 안 씀)
--   - 결제마다 refund_amount = (남은 횟수 − 그 결제에서 쓴 횟수) × 1회 가격(결제 금액 ÷ 충전 횟수)

SET @user_id = 1;

WITH paid AS (
    -- 결제마다 아직 환불하지 않은 횟수
    SELECT p.payment_id,
           p.paddle_transaction_id,
           p.paid_at,
           p.amount,
           p.currency,
           p.credits,
           p.credits - COALESCE(SUM(r.credits), 0) AS left_credits
    FROM payment p
             LEFT JOIN payment_refund r ON r.payment_id = p.payment_id
    WHERE p.user_id = @user_id
    GROUP BY p.payment_id
),
     paid_used AS (
         -- 탈퇴하면 users 행이 없어 남은 크레딧을 0 으로 봅니다(payment 는 5년 보관).
         SELECT GREATEST(SUM(left_credits) - COALESCE((SELECT credits FROM users WHERE user_id = @user_id), 0), 0) AS n
         FROM paid
     ),
     fifo AS (
         -- 먼저 산 결제부터 쓴 횟수를 채웁니다.
         SELECT paid.*,
                LEAST(GREATEST(paid_used.n - (SUM(left_credits) OVER w - left_credits), 0), left_credits) AS used
         FROM paid,
              paid_used
         WINDOW w AS (ORDER BY paid_at, payment_id)
     )
SELECT paddle_transaction_id,
       paid_at,
       amount,
       currency,
       credits,
       left_credits,
       used,
       CASE WHEN amount = 0 THEN NULL ELSE (left_credits - used) * amount DIV credits END AS refund_amount
FROM fifo
ORDER BY paid_at, payment_id;
