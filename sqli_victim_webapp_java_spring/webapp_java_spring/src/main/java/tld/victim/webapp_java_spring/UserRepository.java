package tld.victim.webapp_java_spring;

import org.springframework.data.repository.CrudRepository;
import tld.victim.webapp_java_spring.User;

// This will be AUTO IMPLEMENTED by Spring into a Bean called userRepository

public interface UserRepository extends CrudRepository<User,Integer> {}