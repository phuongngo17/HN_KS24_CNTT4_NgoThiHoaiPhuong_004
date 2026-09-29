package org.example.reservationservice.models.services.impl;

import org.example.reservationservice.exceptions.RoomNotFoundException;
import org.example.reservationservice.models.constants.ReservationStatus;
import org.example.reservationservice.models.dto.requests.CreateReservationDetailRequest;
import org.example.reservationservice.models.dto.requests.CreateReservationRequest;
import org.example.reservationservice.models.dto.responses.ReservationResponse;
import org.example.reservationservice.models.dto.responses.RoomResponse;
import org.example.reservationservice.models.entities.Reservation;
import org.example.reservationservice.models.entities.ReservationDetail;
import org.example.reservationservice.models.repositories.ReservationDetailRepository;
import org.example.reservationservice.models.repositories.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReservationServiceImplTest {

    private ReservationRepository reservationRepository;
    private ReservationDetailRepository reservationDetailRepository;
    private RoomGatewayService roomGatewayService;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private ReservationServiceImpl reservationService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        reservationDetailRepository = mock(ReservationDetailRepository.class);
        roomGatewayService = mock(RoomGatewayService.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        reservationService = new ReservationServiceImpl(
                reservationRepository,
                reservationDetailRepository,
                roomGatewayService,
                kafkaTemplate
        );
    }

    @Test
    void createReservation_success() {
        CreateReservationDetailRequest item = new CreateReservationDetailRequest(1L, 2);
        CreateReservationRequest request = new CreateReservationRequest(
                "Nguyen Van A",
                "a@example.com",
                List.of(item)
        );

        RoomResponse room = new RoomResponse(1L, "101", "Deluxe", 100.0, 5);
        when(roomGatewayService.getRoomById(1L)).thenReturn(room);

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            return Reservation.builder()
                    .id(10L)
                    .customerName(r.getCustomerName())
                    .customerEmail(r.getCustomerEmail())
                    .totalAmount(r.getTotalAmount())
                    .status(r.getStatus())
                    .build();
        });

        when(reservationDetailRepository.save(any(ReservationDetail.class))).thenAnswer(invocation -> {
            ReservationDetail d = invocation.getArgument(0);
            return ReservationDetail.builder()
                    .id(100L)
                    .reservation(d.getReservation())
                    .roomId(d.getRoomId())
                    .nights(d.getNights())
                    .pricePerNight(d.getPricePerNight())
                    .build();
        });

        ReservationResponse response = reservationService.createReservation(request);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("Nguyen Van A", response.customerName());
        assertEquals("a@example.com", response.customerEmail());
        assertEquals(200.0, response.totalAmount());
        assertEquals(ReservationStatus.CONFIRM, response.status());
        assertEquals(1, response.items().size());
        assertEquals(100L, response.items().get(0).id());
        assertEquals(200.0, response.items().get(0).lineTotal());

        verify(kafkaTemplate).send(eq("reservation-created"), eq("a@example.com"));
    }

    @Test
    void createReservation_emptyItems_throwsIllegalArgumentException() {
        CreateReservationRequest request = new CreateReservationRequest(
                "Nguyen Van A",
                "a@example.com",
                Collections.emptyList()
        );

        assertThrows(IllegalArgumentException.class,
                () -> reservationService.createReservation(request));
    }

    @Test
    void createReservation_roomOutOfStock_throwsIllegalArgumentException() {
        CreateReservationDetailRequest item = new CreateReservationDetailRequest(1L, 2);
        CreateReservationRequest request = new CreateReservationRequest(
                "Nguyen Van A",
                "a@example.com",
                List.of(item)
        );

        RoomResponse room = new RoomResponse(1L, "101", "Deluxe", 100.0, 0);
        when(roomGatewayService.getRoomById(1L)).thenReturn(room);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reservationService.createReservation(request));

        assertEquals("Phong da duoc dat, khong the dat phong", ex.getMessage());
        verifyNoInteractions(reservationRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void createReservation_roomNotFound_throwsRoomNotFoundException() {
        CreateReservationDetailRequest item = new CreateReservationDetailRequest(99L, 2);
        CreateReservationRequest request = new CreateReservationRequest(
                "Nguyen Van A",
                "a@example.com",
                List.of(item)
        );

        when(roomGatewayService.getRoomById(99L)).thenThrow(new RoomNotFoundException(99L));

        assertThrows(RoomNotFoundException.class,
                () -> reservationService.createReservation(request));
        verifyNoInteractions(reservationRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void createReservation_serviceUnavailable_throwsIllegalStateException() {
        CreateReservationDetailRequest item = new CreateReservationDetailRequest(1L, 2);
        CreateReservationRequest request = new CreateReservationRequest(
                "Nguyen Van A",
                "a@example.com",
                List.of(item)
        );

        when(roomGatewayService.getRoomById(1L)).thenThrow(new IllegalStateException("Dich vu Room Service hien khong kha dung"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> reservationService.createReservation(request));
        assertEquals("Dich vu Room Service hien khong kha dung", ex.getMessage());
        verifyNoInteractions(reservationRepository);
        verifyNoInteractions(kafkaTemplate);
    }
}
