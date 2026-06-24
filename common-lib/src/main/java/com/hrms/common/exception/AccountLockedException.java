package  com.hrms.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) { super(message); }
}
