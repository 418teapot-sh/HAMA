package com.hama.global.auth;

/**
 * 인증된 사용자 정보. 액세스 토큰에서 꺼낸 userId 만 담습니다. (DB 조회 없음)
 *
 * <p>컨트롤러에서 로그인한 유저 id 를 꺼내는 방법:
 * <pre>{@code
 * @GetMapping("/api/todos")
 * public ApiResponse<...> myTodos(@AuthenticationPrincipal AuthUser authUser) {
 *     Long userId = authUser.userId();
 * }
 * }</pre>
 */
public record AuthUser(Long userId) {
}
