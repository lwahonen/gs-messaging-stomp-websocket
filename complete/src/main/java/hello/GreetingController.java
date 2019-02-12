package hello;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@SpringBootApplication
public class GreetingController {
    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/broker/{token}")
    @SendTo("/broker/topic/{token}")
    public Greeting greeting(@DestinationVariable String token, String message) {
        Logger.getLogger(getClass().getName()).log(Level.INFO, String.format("Writing %s to %s", message, token));
        return new Greeting(message);
    }

    @RequestMapping(path="/broker/post/{token}")
    public String index(@PathVariable("token") String token, @RequestParam(value="message", required=false) String message) {
        simpMessagingTemplate.convertAndSend("/topic/"+token, new Greeting(message));
        return "Sending "+message+" to all listeners on "+token;
    }
}
