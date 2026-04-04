package org.example.lifecomposer.Exception;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController {

    @RequestMapping("/error/403")
    public String handle403() {
        return "error/403";
    }

    @RequestMapping("/error/404")
    public String handle404() {
        return "error/404";
    }

    @RequestMapping("/error/500")
    public String handle500() {
        return "error/500";
    }

    @RequestMapping("/error/general")
    public String handleGeneral() {
        return "error/general";
    }
}
