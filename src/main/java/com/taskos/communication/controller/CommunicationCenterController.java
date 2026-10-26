package com.taskos.communication.controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CommunicationCenterController {
    private static final List<ConnectorCard> CONNECTORS = List.of(
            new ConnectorCard("Email", "Gmail", "Google account consent and Gmail API access are not configured."),
            new ConnectorCard("Email", "Microsoft Outlook", "Microsoft identity and Graph access are not configured."),
            new ConnectorCard("Messaging", "WhatsApp Business", "Business account, approved number, and opt-in handling are not configured."),
            new ConnectorCard("Calendar", "Google Calendar", "Google Calendar authorization is not configured."),
            new ConnectorCard("Calendar", "Microsoft Calendar", "Microsoft Graph calendar access is not configured."),
            new ConnectorCard("Meetings", "Google Meet", "Meeting artifact access is not configured."),
            new ConnectorCard("Meetings", "Microsoft Teams", "Meeting artifact access is not configured."),
            new ConnectorCard("Meetings", "Zoom", "Zoom meeting artifact access is not configured."));

    @GetMapping("/communications")
    public String index(Model model) {
        model.addAttribute("activeNav", "communications");
        model.addAttribute("connectors", CONNECTORS);
        return "communications/index";
    }

    public record ConnectorCard(String category, String name, String requirement) {}
}
