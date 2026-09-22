package pe.ayni.identity.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.identity.application.RequestAccessUseCase;

@RestController
@RequestMapping("/api/v1/access")
public class AccessController {

    private final RequestAccessUseCase requestAccess;

    public AccessController(RequestAccessUseCase requestAccess) {
        this.requestAccess = requestAccess;
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestAccess(
            @Valid @RequestBody RequestAccessRequest request,
            HttpServletRequest httpRequest) {

        requestAccess.execute(
                request.email(),
                httpRequest.getRemoteAddr());
    }
}