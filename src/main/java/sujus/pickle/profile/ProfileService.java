package sujus.pickle.profile;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import sujus.pickle.user.*;

import java.util.List;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final AddressRepository addressRepository;
    private final ProfileImageStorageService imageStorage;

    public ProfileService(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            AddressRepository addressRepository,
            ProfileImageStorageService imageStorage
    ) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.addressRepository = addressRepository;
        this.imageStorage = imageStorage;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        AppUser user = findUser(userId);

        UserProfile profile = profileRepository
                .findById(userId)
                .orElse(null);

        return toResponse(user, profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(
            Long userId,
            UpdateProfileRequest request
    ) {
        AppUser user = findUser(userId);

        user.updateName(
                request.firstName().trim(),
                request.lastName().trim()
        );

        UserProfile profile = getOrCreateProfile(user);

        profile.updatePhone(normalize(request.phone()));

        return toResponse(user, profile);
    }

    @Transactional
    public UserProfileResponse uploadPhoto(Long userId, MultipartFile photo) {
        AppUser user = findUser(userId);
        UserProfile profile = getOrCreateProfile(user);

        String previousImage = profile.getProfileImageName();

        String newImage = imageStorage.store(photo);

        profile.updateProfileImage(newImage);
        profileRepository.saveAndFlush(profile);

        imageStorage.delete(previousImage);

        return toResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public ProfileImageStorageService.StoredImage getPhoto(
            Long userId
    ) {
        UserProfile profile = profileRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Profile photo was not found"
                        )
                );

        if (profile.getProfileImageName() == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Profile photo was not found"
            );
        }

        return imageStorage.load(
                profile.getProfileImageName()
        );
    }

    @Transactional
    public void deletePhoto(Long userId) {
        UserProfile profile = profileRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Profile was not found"
                        )
                );

        String imageName =
                profile.getProfileImageName();

        profile.removeProfileImage();
        profileRepository.saveAndFlush(profile);

        imageStorage.delete(imageName);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(
            Long userId
    ) {
        return addressRepository
                .findAllByUser_IdOrderByDefaultAddressDescCreatedAtAsc(
                        userId
                )
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(
            Long userId,
            AddressRequest request
    ) {
        AppUser user = findUser(userId);

        List<Address> existing =
                addressRepository
                        .findAllByUser_IdOrderByDefaultAddressDescCreatedAtAsc(
                                userId
                        );

        boolean makeDefault =
                existing.isEmpty()
                        || Boolean.TRUE.equals(
                        request.defaultAddress()
                );

        if (makeDefault) {
            addressRepository
                    .clearDefaultAddress(userId);
        }

        Address address = new Address(
                user,
                request.recipientName().trim(),
                request.phone().trim(),
                request.addressLine1().trim(),
                normalize(request.addressLine2()),
                request.city().trim(),
                normalize(request.state()),
                request.postalCode().trim(),
                request.country().trim(),
                makeDefault
        );

        return AddressResponse.from(
                addressRepository.save(address)
        );
    }

    @Transactional
    public AddressResponse updateAddress(
            Long userId,
            Long addressId,
            AddressRequest request
    ) {
        Address address = findAddress(
                userId,
                addressId
        );

        boolean makeDefault =
                Boolean.TRUE.equals(
                        request.defaultAddress()
                );

        if (makeDefault) {
            addressRepository
                    .clearDefaultAddress(userId);
        }

        boolean finalDefault =
                makeDefault
                        || address.isDefaultAddress();

        address.update(
                request.recipientName().trim(),
                request.phone().trim(),
                request.addressLine1().trim(),
                normalize(request.addressLine2()),
                request.city().trim(),
                normalize(request.state()),
                request.postalCode().trim(),
                request.country().trim(),
                finalDefault
        );

        return AddressResponse.from(address);
    }

    @Transactional
    public void deleteAddress(
            Long userId,
            Long addressId
    ) {
        Address address = findAddress(
                userId,
                addressId
        );

        boolean wasDefault =
                address.isDefaultAddress();

        addressRepository.delete(address);
        addressRepository.flush();

        if (wasDefault) {
            addressRepository
                    .findAllByUser_IdOrderByDefaultAddressDescCreatedAtAsc(
                            userId
                    )
                    .stream()
                    .findFirst()
                    .ifPresent(Address::makeDefault);
        }
    }

    private AppUser findUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User was not found"
                        )
                );
    }

    private Address findAddress(
            Long userId,
            Long addressId
    ) {
        return addressRepository
                .findByIdAndUser_Id(
                        addressId,
                        userId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Address was not found"
                        )
                );
    }

    private UserProfile getOrCreateProfile(AppUser user) {
        return profileRepository
                .findById(user.getId())
                .orElseGet(() ->
                        profileRepository.save(new UserProfile(user))
                );
    }

    private UserProfileResponse toResponse(
            AppUser user,
            UserProfile profile
    ) {
        return new UserProfileResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().name(),
                profile == null
                        ? null
                        : profile.getPhone(),
                profile != null
                        && profile.getProfileImageName()
                        != null
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}