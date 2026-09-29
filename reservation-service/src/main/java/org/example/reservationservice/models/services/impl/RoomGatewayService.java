package org.example.reservationservice.models.services.impl;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.clients.RoomClient;
import org.example.reservationservice.exceptions.RoomNotFoundException;
import org.example.reservationservice.exceptions.RoomServiceException;
import org.example.reservationservice.models.dto.responses.RoomResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomGatewayService {

    private final RoomClient roomClient;

    public RoomResponse getRoomById(Long roomId) {
        try {
            RoomResponse response = roomClient.getRoomById(roomId);
            if (response == null) {
                throw new IllegalStateException("Dich vu Room Service hien khong kha dung");
            }
            return response;
        } catch (FeignException.NotFound ex) {
            throw new RoomNotFoundException(roomId);
        } catch (FeignException ex) {
            if (ex.status() == 404) {
                throw new RoomNotFoundException(roomId);
            }
            throw new IllegalStateException("Dich vu Room Service hien khong kha dung", ex);
        } catch (RoomNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Dich vu Room Service hien khong kha dung", ex);
        }
    }
}
