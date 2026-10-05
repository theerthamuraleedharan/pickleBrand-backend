package sujus.pickle.profile;

public record UserProfileResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String role,
        String phone,
        boolean hasProfilePhoto
) {
    public UserProfileResponse withRole(String authenticatedRole) {
        return new UserProfileResponse(id, firstName, lastName, email, authenticatedRole, phone, hasProfilePhoto);
    }
}
