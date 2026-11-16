package oit.is.team7.isdev26team4.room;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class RoomQueryController {

  private static final int DEFAULT_ROOM_NUMBER = 1;

  @GetMapping({ "/Enter", "/Enter/{roomNumber}" })
  public String showRoom(@PathVariable(required = false) String roomNumber, Model model) {
    RoomView room = RoomView.of(parseRoomNumber(roomNumber));
    model.addAttribute("room", room);
    return "room";
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
