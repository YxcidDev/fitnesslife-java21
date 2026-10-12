package com.fitnesslife.gym.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fitnesslife.gym.enums.ClassesStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Document(collection = "functionalTrainings")
@CompoundIndexes({
        @CompoundIndex(name = "status_datetime_idx", def = "{'status': 1, 'datetime': 1}"),
        @CompoundIndex(name = "datetime_status_idx", def = "{'datetime': 1, 'status': 1}"),
        @CompoundIndex(name = "instructor_datetime_idx", def = "{'instructorId': 1, 'datetime': 1}")
})
public class FunctionalTraining {

    /** Used for legacy documents whose duration is empty or not parseable. */
    public static final int DEFAULT_DURATION_MINUTES = 60;

    @Id
    private String id;

    @Indexed(unique = true)
    private int idFunctionalTraining;

    @NotBlank(message = "El nombre del entrenamiento es obligatorio")
    @Indexed
    private String nameTraining;

    /** Display name of the trainer: a snapshot written by the server when the class is assigned. */
    private String instructor;

    /** Id of the trainer's user document. This is the real link used for schedules and conflict checks. */
    @NotBlank(message = "El instructor es obligatorio")
    private String instructorId;

    private String description;

    @Min(value = 1, message = "La capacidad máxima debe ser de al menos 1")
    @Max(value = 50, message = "La capacidad máxima no puede superar 50")
    private int maximumCapacity;

    @NotBlank(message = "La duración es obligatoria")
    @Pattern(regexp = "^[1-9][0-9]{0,2}$",
            message = "La duración debe ser un número entero de minutos entre 1 y 999")
    private String duration;

    @Indexed
    private ClassesStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @CreatedDate
    private Date createdAt;

    @NotNull(message = "La fecha y hora son obligatorias")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    @Indexed
    private Date datetime;

    @NotBlank(message = "La sala es obligatoria")
    private String room;

    private List<Long> userIds = new ArrayList<>();

    public FunctionalTraining() {
    }

    public FunctionalTraining(String id, int idFunctionalTraining, String nameTraining, String instructor,
            String description,
            int maximumCapacity, String duration, ClassesStatus status, Date createdAt, Date datetime, String room,
            List<Long> userIds) {
        this.id = id;
        this.idFunctionalTraining = idFunctionalTraining;
        this.nameTraining = nameTraining;
        this.instructor = instructor;
        this.description = description;
        this.maximumCapacity = maximumCapacity;
        this.duration = duration;
        this.status = ClassesStatus.ACTIVE;
        this.createdAt = createdAt;
        this.datetime = (datetime != null) ? datetime : new Date();
        this.room = room;
        this.userIds = (userIds != null) ? userIds : new ArrayList<>();
    }

    /** Start of the class in the server time zone, or null if the class has no date. */
    public LocalDateTime startsAt() {
        return datetime == null ? null : LocalDateTime.ofInstant(datetime.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Duration in minutes. New input is validated as a plain integer, but legacy documents
     * may contain free text (e.g. "45 min"); in that case the first run of digits is used.
     */
    public int durationMinutes() {
        if (duration == null) {
            return DEFAULT_DURATION_MINUTES;
        }

        StringBuilder digits = new StringBuilder();
        for (char character : duration.toCharArray()) {
            if (Character.isDigit(character)) {
                digits.append(character);
            } else if (digits.length() > 0) {
                break;
            }
        }

        if (digits.length() == 0 || digits.length() > 4) {
            return DEFAULT_DURATION_MINUTES;
        }

        int minutes = Integer.parseInt(digits.toString());
        return minutes > 0 ? minutes : DEFAULT_DURATION_MINUTES;
    }

    /** End of the class (exclusive), or null if the class has no date. */
    public LocalDateTime endsAt() {
        LocalDateTime start = startsAt();
        return start == null ? null : start.plusMinutes(durationMinutes());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getIdFunctionalTraining() {
        return idFunctionalTraining;
    }

    public void setIdFunctionalTraining(int idFunctionalTraining) {
        this.idFunctionalTraining = idFunctionalTraining;
    }

    public String getNameTraining() {
        return nameTraining;
    }

    public void setNameTraining(String nameTraining) {
        this.nameTraining = nameTraining;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public String getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(String instructorId) {
        this.instructorId = instructorId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMaximumCapacity() {
        return maximumCapacity;
    }

    public void setMaximumCapacity(int maximumCapacity) {
        this.maximumCapacity = maximumCapacity;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public ClassesStatus getStatus() {
        return status;
    }

    public void setStatus(ClassesStatus status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getDatetime() {
        return datetime;
    }

    public void setDatetime(Date datetime) {
        this.datetime = datetime;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public List<Long> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }
}