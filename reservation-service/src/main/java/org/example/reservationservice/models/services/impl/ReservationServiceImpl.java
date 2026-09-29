package org.example.reservationservice.models.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.reservationservice.models.constants.ReservationStatus;
import org.example.reservationservice.models.dto.requests.CreateReservationDetailRequest;
import org.example.reservationservice.models.dto.requests.CreateReservationRequest;
import org.example.reservationservice.models.dto.responses.ReservationDetailResponse;
import org.example.reservationservice.models.dto.responses.ReservationResponse;
import org.example.reservationservice.models.dto.responses.RoomResponse;
import org.example.reservationservice.models.entities.Reservation;
import org.example.reservationservice.models.entities.ReservationDetail;
import org.example.reservationservice.models.repositories.ReservationDetailRepository;
import org.example.reservationservice.models.repositories.ReservationRepository;
import org.example.reservationservice.models.services.ReservationService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationServiceImpl implements ReservationService {

    private static final String TOPIC_RESERVATION_CREATED = "reservation-created";

    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final RoomGatewayService roomGatewayService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request) {
        List<CreateReservationDetailRequest> items = request.items();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Reservation must contain at least one item");
        }

        double totalAmount = 0.0;
        List<RoomResponse> rooms = new ArrayList<>();

        for (CreateReservationDetailRequest item : items) {
            RoomResponse room = roomGatewayService.getRoomById(item.roomId());
            if (room.stock() != null && room.stock() <= 0) {
                throw new IllegalArgumentException("Phong da duoc dat, khong the dat phong");
            }
            double pricePerNight = room.pricePerNight() != null ? room.pricePerNight() : 0.0;
            totalAmount += pricePerNight * item.nights();
            rooms.add(room);
        }

        Reservation reservation = Reservation.builder()
                .customerName(request.customerName())
                .customerEmail(request.customerEmail())
                .totalAmount(totalAmount)
                .status(ReservationStatus.CONFIRM)
                .build();
        reservation = reservationRepository.save(reservation);

        List<ReservationDetailResponse> detailResponses = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            CreateReservationDetailRequest item = items.get(i);
            RoomResponse room = rooms.get(i);
            double pricePerNight = room.pricePerNight() != null ? room.pricePerNight() : 0.0;
            double lineTotal = pricePerNight * item.nights();

            ReservationDetail detail = ReservationDetail.builder()
                    .reservation(reservation)
                    .roomId(room.roomId())
                    .nights(item.nights())
                    .pricePerNight(pricePerNight)
                    .build();
            detail = reservationDetailRepository.save(detail);

            detailResponses.add(new ReservationDetailResponse(
                    detail.getId(),
                    room.roomId(),
                    room.roomNumber(),
                    room.roomType(),
                    item.nights(),
                    pricePerNight,
                    lineTotal
            ));
        }

        try {
            kafkaTemplate.send(TOPIC_RESERVATION_CREATED, request.customerEmail());
        } catch (Exception ex) {
            log.error("Failed to publish reservation created event: {}", ex.getMessage());
        }

        return new ReservationResponse(
                reservation.getId(),
                reservation.getCustomerName(),
                reservation.getCustomerEmail(),
                reservation.getTotalAmount(),
                reservation.getStatus(),
                detailResponses
        );
    }
}
