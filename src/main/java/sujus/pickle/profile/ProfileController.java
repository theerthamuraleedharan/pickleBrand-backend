package sujus.pickle.profile;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
            @AuthenticationPrincipal Jwt jwt
    ) {
        return profileService.getProfile(
                getUserId(jwt)
        );
    }

    @PutMapping
    public UserProfileResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid
            @RequestBody UpdateProfileRequest request
    ) {
        return profileService.updateProfile(
                getUserId(jwt),
                request
        );
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse uploadPhoto(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("photo") MultipartFile photo
    ) {
        return profileService.uploadPhoto(
                getUserId(jwt),
                photo
        );
    }

    @GetMapping("/photo")
    public ResponseEntity<Resource> getPhoto(@AuthenticationPrincipal Jwt jwt) {
        ProfileImageStorageService.StoredImage image = profileService.getPhoto(getUserId(jwt));

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
            @AuthenticationPrincipal Jwt jwt
    ) {
        profileService.deletePhoto(
                getUserId(jwt)
        );
    }

    @GetMapping("/addresses")
    public List<AddressResponse> getAddresses(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return profileService.getAddresses(
                getUserId(jwt)
        );
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse createAddress(
            @AuthenticationPrincipal Jwt jwt,
            @Valid
            @RequestBody AddressRequest request
    ) {
        return profileService.createAddress(
                getUserId(jwt),
                request
        );
    }

    @PutMapping("/addresses/{addressId}")
    public AddressResponse updateAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long addressId,
            @Valid
            @RequestBody AddressRequest request
    ) {
        return profileService.updateAddress(
                getUserId(jwt),
                addressId,
                request
        );
    }

    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long addressId
    ) {
        profileService.deleteAddress(
                getUserId(jwt),
                addressId
        );
    }

    private Long getUserId(Jwt jwt) {
        Number claim = jwt.getClaim("userId");

        if (claim == null) {
            throw new IllegalStateException(
                    "JWT does not contain userId"
            );
        }

        return claim.longValue();
    }
}