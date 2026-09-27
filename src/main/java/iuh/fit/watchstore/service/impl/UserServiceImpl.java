package iuh.fit.watchstore.service.impl;

import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.exception.InvalidOperationException;
import iuh.fit.watchstore.exception.ResourceNotFoundException;
import iuh.fit.watchstore.repository.OrderRepository;
import iuh.fit.watchstore.repository.UserRepository;
import iuh.fit.watchstore.service.NotificationService;
import iuh.fit.watchstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    public User createUser(User user) {
        User savedUser = userRepository.save(user);
        notificationService.sendRegistrationEmail(savedUser.getEmail(), savedUser.getFullName());
        return savedUser;
    }

    @Override
    public User updateUser(Long id, User userDetails) {
        User user = getUserById(id);
        user.setFullName(userDetails.getFullName());
        user.setPhone(userDetails.getPhone());
        user.setAddress(userDetails.getAddress());
        user.setRole(userDetails.getRole());
        return userRepository.save(user);
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        if (orderRepository.existsByUser_Id(id)) {
            throw new InvalidOperationException("Cannot delete User because they have an order history");
        }
        userRepository.deleteById(id);
    }
}
