package org.example.lifecomposer.Exception;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController {

    @RequestMapping("/error/403")
    public String handle403() {
        return "error/403";
    }
}
