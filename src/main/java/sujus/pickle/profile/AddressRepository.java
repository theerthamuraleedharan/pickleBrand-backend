package sujus.pickle.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findAllByUser_IdOrderByDefaultAddressDescCreatedAtAsc(Long userId);

    Optional<Address> findByIdAndUser_Id(
            Long addressId,
            Long userId
    );

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Address address
        SET address.defaultAddress = false
        WHERE address.user.id = :userId
          AND address.defaultAddress = true
    """)
    int clearDefaultAddress(
            @Param("userId") Long userId
    );
}