-- Paddle 결제 완료 1건당 1행. 같은 거래의 웹훅이 여러 번 와도 한 번만 저장되도록 거래 ID 가 unique 입니다.
CREATE TABLE payment
(
    payment_id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    paddle_transaction_id VARCHAR(255) NOT NULL,
    amount                INT          NOT NULL,
    currency              VARCHAR(3)   NOT NULL,
    -- 이 결제로 충전한 크레딧. 환불할 때 회수할 횟수입니다.
    credits               INT          NOT NULL,
    paid_at               DATETIME(6)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (payment_id),
    CONSTRAINT uk_payment_paddle_transaction_id UNIQUE (paddle_transaction_id)
);
