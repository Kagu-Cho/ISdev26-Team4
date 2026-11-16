package oit.is.team7.isdev26team4.room;

public record RoomView(String roomName, int participantCount, int vacancyCount) {

  public static RoomView of(int roomNumber) {
    if (roomNumber < 1) {
      throw new IllegalArgumentException("room number must be positive");
    }

    int participantCount = 0;
    int capacity = 4;
    return new RoomView("Room(" + roomNumber + ")", participantCount, capacity - participantCount);
  }
}
