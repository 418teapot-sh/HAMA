-- Paddle 환불·차지백(adjustment) 1건당 1행. 같은 조정의 웹훅이 여러 번 와도 한 번만 회수하도록 조정 ID 가 unique 입니다.
-- payment 처럼 전자상거래법상 보관 대상이라 회원탈퇴 때 지우지 않습니다.
CREATE TABLE payment_refund
(
    payment_refund_id    BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id           BIGINT       NOT NULL,
    paddle_adjustment_id VARCHAR(255) NOT NULL,
    -- refund(환불) 또는 chargeback(카드사 지불 거절)
    action               VARCHAR(20)  NOT NULL,
    -- 환불 금액. payment.amount 와 같은 통화의 최소 단위입니다.
    amount               INT          NOT NULL,
    -- 이 환불로 회수할 크레딧(남은 크레딧이 모자라 덜 뺐어도 계산한 횟수). 환불 계산 SQL 이 씁니다.
    credits              INT          NOT NULL,
    -- 실제로 뺀 크레딧(남은 크레딧이 모자랐거나 탈퇴했으면 credits 보다 적음). 차지백이 취소되면 이만큼 돌려줍니다.
    taken_credits        INT          NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (payment_refund_id),
    CONSTRAINT uk_payment_refund_paddle_adjustment_id UNIQUE (paddle_adjustment_id),
    INDEX idx_payment_refund_payment_id (payment_id)
);
