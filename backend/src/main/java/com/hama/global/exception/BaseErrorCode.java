package com.hama.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 도메인 에러코드 enum이 구현하는 인터페이스.
 *
 * <p>팀원마다 자기 도메인 패키지에 이 인터페이스를 구현하는 enum을 각자 만듭니다.
 * ErrorCode 파일 하나를 여럿이 같이 건드리면 PR 마다 merge 충돌이 나기 때문입니다.
 * {@link GlobalErrorCode} 에는 특정 도메인에 속하지 않는 공통 에러만 둡니다.
 *
 * <p>예시:
 * <pre>{@code
 * @Getter
 * @RequiredArgsConstructor
 * public enum TodoErrorCode implements BaseErrorCode {
 *     TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "투두를 찾을 수 없습니다.");
 *
 *     private final HttpStatus status;
 *     private final String message;
 * }
 * }</pre>
 */
public interface BaseErrorCode {

    HttpStatus getStatus();

    String getMessage();

    /** enum이 구현하면 자동으로 제공됩니다. (Enum.name()) */
    String name();
}
