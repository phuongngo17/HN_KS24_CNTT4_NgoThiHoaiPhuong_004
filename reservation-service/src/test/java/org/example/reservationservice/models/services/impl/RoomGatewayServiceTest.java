package org.example.reservationservice.models.services.impl;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.example.reservationservice.clients.RoomClient;
import org.example.reservationservice.exceptions.RoomNotFoundException;
import org.example.reservationservice.models.dto.responses.RoomResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomGatewayServiceTest {

    private RoomClient roomClient;
    private RoomGatewayService gatewayService;

    @BeforeEach
    void setUp() {
        roomClient = mock(RoomClient.class);
        gatewayService = new RoomGatewayService(roomClient);
    }

    @Test
    void getRoomById_returnsRoom() {
        RoomResponse expected = new RoomResponse(1L, "101", "Deluxe", 150.0, 5);
        when(roomClient.getRoomById(1L)).thenReturn(expected);

        RoomResponse actual = gatewayService.getRoomById(1L);

        assertEquals(expected, actual);
        verify(roomClient).getRoomById(1L);
    }

    @Test
    void getRoomById_returnsNull_throwsIllegalStateException() {
        when(roomClient.getRoomById(1L)).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gatewayService.getRoomById(1L));

        assertEquals("Dich vu Room Service hien khong kha dung", ex.getMessage());
    }

    @Test
    void getRoomById_feignNotFound_throwsRoomNotFoundException() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/rooms/99", Collections.emptyMap(), null, new RequestTemplate());
        when(roomClient.getRoomById(99L)).thenThrow(new FeignException.NotFound("Not Found", request, null, null));

        assertThrows(RoomNotFoundException.class,
                () -> gatewayService.getRoomById(99L));
    }

    @Test
    void getRoomById_feignInternalError_throwsIllegalStateException() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/rooms/1", Collections.emptyMap(), null, new RequestTemplate());
        when(roomClient.getRoomById(1L)).thenThrow(new FeignException.InternalServerError("Internal Server Error", request, null, null));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gatewayService.getRoomById(1L));

        assertEquals("Dich vu Room Service hien khong kha dung", ex.getMessage());
    }
}
