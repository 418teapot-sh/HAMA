package com.hama.domain.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hama.domain.calendar.dto.CalendarResponse;
import com.hama.domain.calendar.service.CalendarService;
import com.hama.global.auth.AuthUser;
import com.hama.global.exception.BusinessException;
import com.hama.global.exception.GlobalErrorCode;
import com.hama.global.exception.GlobalExceptionHandler;
import com.hama.global.response.ApiResponse;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class CalendarExceptionHandlerTest {

    private final GlobalExceptionHandler common = spy(new GlobalExceptionHandler());
    private final CalendarExceptionHandler handler = new CalendarExceptionHandler(common);

    @ParameterizedTest
    @MethodSource("requestExceptions")
    void 파일응답에서도_공통_요청오류의_상태와_코드를_보존한다(Exception exception, HttpStatus status, String code) {
        ResponseEntity<ApiResponse<Void>> response = handler.handle(exception);
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().error().code()).isEqualTo(code);
        assertThat(response.getBody().error().fields()).isNull();
    }

    private static Stream<Arguments> requestExceptions() throws Exception {
        return Stream.of(
                Arguments.of(new MethodArgumentTypeMismatchException("abc", Integer.class, "from", parameter(), null),
                        HttpStatus.BAD_REQUEST, "BINDING_ERROR"),
                Arguments.of(new ConversionFailedException(TypeDescriptor.valueOf(String.class),
                        TypeDescriptor.valueOf(Integer.class), "abc", new NumberFormatException("abc")),
                        HttpStatus.BAD_REQUEST, "BINDING_ERROR"),
                Arguments.of(new HttpMessageNotReadableException("invalid JSON", new MockHttpInputMessage(new byte[0])),
                        HttpStatus.BAD_REQUEST, "PARSING_ERROR"),
                Arguments.of(new MissingServletRequestPartException("file"), HttpStatus.BAD_REQUEST, "PARSING_ERROR"),
                Arguments.of(new NoResourceFoundException(HttpMethod.GET, "/missing", "missing"),
                        HttpStatus.NOT_FOUND, "NO_RESOURCE_FOUND"),
                Arguments.of(new HttpMediaTypeNotSupportedException("wrong media"),
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "NOT_SUPPORTED_MEDIA"),
                Arguments.of(new MultipartException("invalid multipart"), HttpStatus.BAD_REQUEST, "INVALID_MULTIPART"),
                Arguments.of(new AccessDeniedException("denied"), HttpStatus.FORBIDDEN, "FORBIDDEN"),
                Arguments.of(new InsufficientAuthenticationException("no auth"), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"),
                Arguments.of(new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)),
                        HttpStatus.NOT_ACCEPTABLE, "CALENDAR_NOT_ACCEPTABLE"));
    }

    @Test
    void 검증오류는_같은필드의_모든메시지와_traceId를_유지한다() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "title", "필수입니다."));
        binding.addError(new FieldError("request", "title", "2자 이상입니다."));
        MDC.put("traceId", "calendar-error-trace");
        try {
            ResponseEntity<ApiResponse<Void>> response = handler.handle(new MethodArgumentNotValidException(parameter(), binding));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
            assertThat(response.getBody().error().fields()).containsEntry("title", "필수입니다., 2자 이상입니다.");
            assertThat(response.getBody().traceId()).isEqualTo("calendar-error-trace");
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    void 메서드오류는_405와_Allow헤더를_유지한다() {
        ResponseEntity<ApiResponse<Void>> response = handler.handle(
                new HttpRequestMethodNotSupportedException("POST", java.util.List.of("GET")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getHeaders().getAllow()).containsExactly(HttpMethod.GET);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void 비즈니스_서버오류의_원인을_공통로그에_넘기고_기본문구만_노출한다() {
        BusinessException exception = new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR,
                new IllegalStateException("internal details"));
        ResponseEntity<ApiResponse<Void>> response = handler.handle(exception);
        verify(common).handleBusinessException(exception);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().message()).isEqualTo(GlobalErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        assertThat(response.getBody().error().message()).doesNotContain("internal details");
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 알수없는_오류는_원래예외를_공통처리기에_전달한다() {
        Exception exception = new IllegalStateException("internal details");
        ResponseEntity<ApiResponse<Void>> response = handler.handle(exception);
        verify(common).handleGeneralException(exception);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().error().message()).doesNotContain("internal details");
    }

    @Test
    void 조회응답을_파일로_요청하면_406_JSON으로_응답한다() throws Exception {
        CalendarService service = mock(CalendarService.class);
        when(service.get(eq(1L), any())).thenReturn(new CalendarResponse(List.of()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CalendarController(service, mock(com.hama.domain.todo.service.AiTodoPostponeService.class)))
                .setControllerAdvice(handler)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthUser.class;
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binderFactory) {
                        return new AuthUser(1L);
                    }
                }).build();
        mvc.perform(get("/api/v1/calendar").param("from", "2026-10-01").param("to", "2026-10-01")
                        .accept("text/calendar"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CALENDAR_NOT_ACCEPTABLE"));
    }

    private static MethodParameter parameter() throws NoSuchMethodException {
        return new MethodParameter(CalendarController.class.getMethod("get", AuthUser.class,
                String.class, String.class, String.class), 1);
    }
}
