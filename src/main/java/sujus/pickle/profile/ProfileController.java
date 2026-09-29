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

    @GetMapping
    public UserProfileResponse getProfile(
            Authentication authentication
    ) {
        return profileService.getProfile(
                CurrentUser.id(authentication)
        ).withRole(CurrentUser.role(authentication));
    }

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

    @DeleteMapping("/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(
            Authentication authentication
    ) {
        profileService.deletePhoto(
                CurrentUser.id(authentication)
        );
    }

    @GetMapping("/addresses")
    public List<AddressResponse> getAddresses(
            Authentication authentication
    ) {
        return profileService.getAddresses(
                CurrentUser.id(authentication)
        );
    }

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
