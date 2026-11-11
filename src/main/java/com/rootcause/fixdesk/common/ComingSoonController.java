package com.rootcause.fixdesk.common;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * TEMPORARY. Placeholders for the three start pages, so login leads somewhere
 * before the modules exist. When a module's real controller maps one of these
 * URLs, delete that one method here (two controllers on the same URL stop the
 * application from starting). Delete this class once all three are gone.
 */
@Controller
public class ComingSoonController {

    // Module 2 (ticket submission) replaces this.
    @GetMapping("/tickets/mine")
    public String myTickets(Model model) {
        return page(model, "My Tickets");
    }


    // Module 3 (AI triage and assignment) replaces this.
    @GetMapping("/triage/queue")
    public String queue(Model model) {
        return page(model, "Ticket Queue");
    }


    // Module 7 (reports and dashboard) replaces this.
    @GetMapping("/reports")
    public String dashboard(Model model) {
        return page(model, "Dashboard");
    }


    private static String page(Model model, String pageName) {
        model.addAttribute("pageName", pageName);
        return "common/coming-soon";
    }
}
