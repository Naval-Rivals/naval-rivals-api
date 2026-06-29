package com.navalrivals.domain.chat_test;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ChatMessage {
    private String from;
    private String content;
    private LocalDateTime timestamp;


    public ChatMessage(String from, String content){
        this.from = from;
        this.content = content;
        timestamp = LocalDateTime.now();
    }
}
