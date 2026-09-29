package sujus.pickle;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("local")
class PickleApplicationTests extends PostgresIntegrationTest {

	@Test
	void contextLoads() {
	}

}
