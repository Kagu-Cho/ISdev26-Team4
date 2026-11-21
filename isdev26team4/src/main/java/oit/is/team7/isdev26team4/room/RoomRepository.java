package oit.is.team7.isdev26team4.room;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoomRepository {

  record RoomParticipant(String roomNumber, String participantId, String displayName, int slotNumber) {
  }

  @Select("SELECT COUNT(*) FROM room_participant WHERE room_number = #{roomNumber}")
  int countParticipants(String roomNumber);

  @Select("SELECT COUNT(*) > 0 FROM room_participant WHERE room_number = #{roomNumber} AND participant_id = #{participantId}")
  boolean existsParticipant(String roomNumber, String participantId);

  @Insert("""
      INSERT INTO room_participant (room_number, participant_id, display_name, slot_number)
      SELECT #{roomNumber}, #{participantId}, #{displayName}, COALESCE(MAX(slot_number), 0) + 1
      FROM room_participant
      WHERE room_number = #{roomNumber}
        AND NOT EXISTS (
          SELECT 1 FROM room_participant
          WHERE room_number = #{roomNumber} AND participant_id = #{participantId}
        )
      HAVING COUNT(*) < 4
      """)
  int joinIfVacant(String roomNumber, String participantId, String displayName);
}
