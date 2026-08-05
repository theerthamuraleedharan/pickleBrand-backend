package sujus.pickle.profile;

import jakarta.persistence.*;
import sujus.pickle.user.AppUser;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(length = 30)
    private String phone;

    @Column(name = "profile_image_name", length = 255)
    private String profileImageName;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserProfile() {
    }

    public UserProfile(AppUser user) {
        this.user = user;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void updatePhone(String phone) {
        this.phone = phone;
    }

    public void updateProfileImage(String profileImageName) {
        this.profileImageName = profileImageName;
    }

    public void removeProfileImage() {
        this.profileImageName = null;
    }

    public String getPhone() {
        return phone;
    }

    public String getProfileImageName() {
        return profileImageName;
    }
}