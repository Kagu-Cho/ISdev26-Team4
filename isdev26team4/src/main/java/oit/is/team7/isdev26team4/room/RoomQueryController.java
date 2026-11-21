package oit.is.team7.isdev26team4.room;

import java.security.Principal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class RoomQueryController {

  private static final int DEFAULT_ROOM_NUMBER = 1;
  private final ObjectProvider<RoomRepository> repositoryProvider;

  public RoomQueryController(ObjectProvider<RoomRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @GetMapping({ "/Enter", "/Enter/{roomNumber}" })
  public String showRoom(@PathVariable(required = false) String roomNumber, Model model) {
    int number = parseRoomNumber(roomNumber);
    RoomView room = roomView(number);
    model.addAttribute("room", room);
    model.addAttribute("roomNumber", number);
    return "room";
  }

  @PostMapping({ "/Enter/{roomNumber}/participants", "/Enter/participants" })
  public String joinRoom(
      @PathVariable(required = false) String roomNumber,
      Principal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login is required");
    }

    int number = parseRoomNumber(roomNumber);
    RoomRepository repository = repositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "room repository is unavailable");
    }

    String participantId = principal.getName();
    if (repository.existsParticipant(String.valueOf(number), participantId)) {
      return "redirect:/Enter/" + number;
    }

    try {
      if (repository.joinIfVacant(String.valueOf(number), participantId, participantId) == 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "room is full");
      }
    } catch (DuplicateKeyException exception) {
      if (repository.existsParticipant(String.valueOf(number), participantId)) {
        return "redirect:/Enter/" + number;
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "room is full", exception);
    }

    return "redirect:/Enter/" + number;
  }

  private RoomView roomView(int roomNumber) {
    RoomRepository repository = repositoryProvider.getIfAvailable();
    if (repository == null) {
      return RoomView.of(roomNumber);
    }
    int participantCount = repository.countParticipants(String.valueOf(roomNumber));
    return new RoomView("Room(" + roomNumber + ")", participantCount, 4 - participantCount);
  }

  private int parseRoomNumber(String roomNumber) {
    if (roomNumber == null) {
      return DEFAULT_ROOM_NUMBER;
    }

    try {
      return Integer.parseInt(roomNumber);
    } catch (NumberFormatException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "room number must be a number", exception);
    }
  }
}
