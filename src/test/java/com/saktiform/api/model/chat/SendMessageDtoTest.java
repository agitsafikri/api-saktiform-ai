package com.saktiform.api.model.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SendMessageDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private SendMessageDto read(String json) throws Exception {
        return mapper.readValue(json, SendMessageDto.class);
    }

    @Test
    void singleStringBecomesList() throws Exception {
        var dto = read("{\"messageType\":\"IMAGE\",\"mediaLink\":\"https://x/a.jpg\"}");
        assertEquals(List.of("https://x/a.jpg"), dto.getMediaLink());
    }

    @Test
    void arrayIsKept() throws Exception {
        var dto = read("{\"messageType\":\"IMAGE\",\"mediaLink\":[\"a\",\"b\"]}");
        assertEquals(List.of("a", "b"), dto.getMediaLink());
    }

    @Test
    void nullStaysNull() throws Exception {
        assertNull(read("{\"messageType\":\"TEXT\",\"mediaLink\":null}").getMediaLink());
    }

    @Test
    void absentStaysNull() throws Exception {
        assertNull(read("{\"messageType\":\"TEXT\"}").getMediaLink());
    }

    @Test
    void emptyArrayIsEmpty() throws Exception {
        assertEquals(List.of(), read("{\"messageType\":\"IMAGE\",\"mediaLink\":[]}").getMediaLink());
    }
}
