package com.rootcause.fixdesk.common;

import com.rootcause.fixdesk.security.SessionUser;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

/** "/" sends each role to its own start page. Visitors without a session are sent to /login first. */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(@SessionAttribute(SessionUser.ATTR) SessionUser user) {
        return "redirect:" + user.role().startPage();
    }
}
