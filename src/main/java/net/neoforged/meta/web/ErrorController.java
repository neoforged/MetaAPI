package net.neoforged.meta.web;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.stereotype.Controller;

/**
 * This subclass only exists so that {@link UiController} contributes its model attributes.
 */
@Controller
public class ErrorController extends BasicErrorController {
    public ErrorController(ErrorAttributes errorAttributes, WebProperties webProperties) {
        super(errorAttributes, webProperties.getError());
    }
}
