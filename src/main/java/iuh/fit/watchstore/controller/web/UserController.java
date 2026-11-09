package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.repository.UserRepository;
import iuh.fit.watchstore.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Controller
@RequestMapping("/profile")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private String extractEmail(Object principal) {
        if (principal == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                principal = auth.getPrincipal();
            }
        }
        if (principal == null) {
            return null;
        }

        if (principal instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null && !email.trim().isEmpty()) {
                return email.trim();
            }
        }

        if (principal instanceof CustomUserDetails customUserDetails) {
            if (customUserDetails.getUser() != null && customUserDetails.getUser().getEmail() != null) {
                return customUserDetails.getUser().getEmail().trim();
            }
            return customUserDetails.getUsername() != null ? customUserDetails.getUsername().trim() : null;
        }

        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername() != null ? userDetails.getUsername().trim() : null;
        }

        if (principal instanceof String principalStr && !"anonymousUser".equals(principalStr)) {
            return principalStr.trim();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return auth.getName().trim();
        }

        return null;
    }

    @GetMapping
    public String profilePage(@AuthenticationPrincipal Object principal, Model model) {
        String email = extractEmail(principal);
        if (email == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        return "web/profile";
    }

    @PostMapping("/update")
    public String updateProfile(@AuthenticationPrincipal Object principal,
                                @RequestParam("fullName") String fullName,
                                @RequestParam(value = "phone", required = false) String phone,
                                @RequestParam(value = "avatar", required = false) String avatar,
                                @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
                                @RequestParam(value = "province", required = false) String province,
                                @RequestParam(value = "district", required = false) String district,
                                @RequestParam(value = "ward", required = false) String ward,
                                @RequestParam(value = "streetAddress", required = false) String streetAddress,
                                RedirectAttributes redirectAttributes) {
        String email = extractEmail(principal);
        if (email == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByEmail(email).orElse(null);
        if (currentUser == null) {
            return "redirect:/login";
        }

        currentUser.setFullName(fullName != null ? fullName.trim() : currentUser.getFullName());
        currentUser.setPhone(phone != null && !phone.trim().isEmpty() ? phone.trim() : null);

        // Handle uploaded avatar file
        if (avatarFile != null && !avatarFile.isEmpty()) {
            try {
                Path uploadPath = Paths.get("uploads", "avatars");
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String originalFileName = avatarFile.getOriginalFilename();
                String cleanFileName = originalFileName != null ? StringUtils.cleanPath(originalFileName) : "avatar.png";
                if (cleanFileName.isEmpty() || cleanFileName.contains("..")) {
                    cleanFileName = "avatar.png";
                }
                String uniqueFileName = UUID.randomUUID().toString() + "_" + cleanFileName;

                Path targetFile = uploadPath.resolve(uniqueFileName);
                Files.copy(avatarFile.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

                currentUser.setAvatar("/uploads/avatars/" + uniqueFileName);
            } catch (IOException e) {
                redirectAttributes.addFlashAttribute("error", "Lỗi tải ảnh lên: " + e.getMessage());
                return "redirect:/profile";
            }
        } else if (avatar != null && !avatar.trim().isEmpty()) {
            currentUser.setAvatar(avatar.trim());
        }

        currentUser.setProvince(province != null && !province.trim().isEmpty() ? province.trim() : null);
        currentUser.setDistrict(district != null && !district.trim().isEmpty() ? district.trim() : null);
        currentUser.setWard(ward != null && !ward.trim().isEmpty() ? ward.trim() : null);
        currentUser.setStreetAddress(streetAddress != null && !streetAddress.trim().isEmpty() ? streetAddress.trim() : null);

        // Build combined address snapshot
        StringBuilder fullAddress = new StringBuilder();
        if (streetAddress != null && !streetAddress.trim().isEmpty()) {
            fullAddress.append(streetAddress.trim());
        }
        if (ward != null && !ward.trim().isEmpty()) {
            if (fullAddress.length() > 0) fullAddress.append(", ");
            fullAddress.append(ward.trim());
        }
        if (district != null && !district.trim().isEmpty()) {
            if (fullAddress.length() > 0) fullAddress.append(", ");
            fullAddress.append(district.trim());
        }
        if (province != null && !province.trim().isEmpty()) {
            if (fullAddress.length() > 0) fullAddress.append(", ");
            fullAddress.append(province.trim());
        }
        currentUser.setAddress(fullAddress.length() > 0 ? fullAddress.toString() : null);

        userRepository.save(currentUser);

        // Synchronize in-memory principal so Navbar immediately shows updated name & avatar
        if (principal instanceof CustomUserDetails customUserDetails && customUserDetails.getUser() != null) {
            customUserDetails.getUser().setFullName(currentUser.getFullName());
            customUserDetails.getUser().setAvatar(currentUser.getAvatar());
            customUserDetails.getUser().setPhone(currentUser.getPhone());
            customUserDetails.getUser().setAddress(currentUser.getAddress());
        }

        redirectAttributes.addFlashAttribute("success", "Cập nhật hồ sơ cá nhân thành công!");
        return "redirect:/profile";
    }
}
