package sujus.pickle.profile;

import jakarta.validation.Valid;
import sujus.pickle.common.CurrentUser;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(
            ProfileService profileService
    ) {
        this.profileService = profileService;
    }

    // Returns the current user's profile plus the role from the active token.
    @GetMapping
    public UserProfileResponse getProfile(
            Authentication authentication
    ) {
        return profileService.getProfile(
                CurrentUser.id(authentication)
        ).withRole(CurrentUser.role(authentication));
    }

    // Updates the user's display name and profile contact fields.
    @PutMapping
    public UserProfileResponse updateProfile(
            Authentication authentication,
            @Valid
            @RequestBody UpdateProfileRequest request
    ) {
        return profileService.updateProfile(
                CurrentUser.id(authentication),
                request
        ).withRole(CurrentUser.role(authentication));
    }

    // Uploads a new profile image and stores it in the configured filesystem path.
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse uploadPhoto(
            Authentication authentication,
            @RequestPart("photo") MultipartFile photo
    ) {
        return profileService.uploadPhoto(
                CurrentUser.id(authentication),
                photo
        ).withRole(CurrentUser.role(authentication));
    }

    // Reads the current user's uploaded profile photo.
    @GetMapping("/photo")
    public ResponseEntity<Resource> getPhoto(Authentication authentication) {
        ProfileImageStorageService.StoredImage image = profileService.getPhoto(CurrentUser.id(authentication));

        return ResponseEntity
                .ok()
                .contentType(image.mediaType())
                .cacheControl(
                        CacheControl.noCache()
                )
                .body(image.resource());
    }

    // Deletes the current user's profile photo.
    @DeleteMapping("/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(
            Authentication authentication
    ) {
        profileService.deletePhoto(
                CurrentUser.id(authentication)
        );
    }

    // Returns all saved addresses for the authenticated user.
    @GetMapping("/addresses")
    public List<AddressResponse> getAddresses(
            Authentication authentication
    ) {
        return profileService.getAddresses(
                CurrentUser.id(authentication)
        );
    }

    // Creates a new saved address for the authenticated user.
    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse createAddress(
            Authentication authentication,
            @Valid
            @RequestBody AddressRequest request
    ) {
        return profileService.createAddress(
                CurrentUser.id(authentication),
                request
        );
    }

    // Updates an existing saved address and optionally changes the default address flag.
    @PutMapping("/addresses/{addressId}")
    public AddressResponse updateAddress(
            Authentication authentication,
            @PathVariable Long addressId,
            @Valid
            @RequestBody AddressRequest request
    ) {
        return profileService.updateAddress(
                CurrentUser.id(authentication),
                addressId,
                request
        );
    }

    // Deletes one saved address for the current user.
    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(
            Authentication authentication,
            @PathVariable Long addressId
    ) {
        profileService.deleteAddress(
                CurrentUser.id(authentication),
                addressId
        );
    }

}
