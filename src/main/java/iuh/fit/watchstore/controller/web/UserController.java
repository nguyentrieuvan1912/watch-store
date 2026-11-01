package iuh.fit.watchstore.controller.web;

import iuh.fit.watchstore.entity.User;
import iuh.fit.watchstore.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    @GetMapping
    public String profilePage(Model model) {
        User user = getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        return "web/profile";
    }

    @PostMapping("/update")
    public String updateProfile(@RequestParam("fullName") String fullName,
                                @RequestParam(value = "phone", required = false) String phone,
                                @RequestParam(value = "avatar", required = false) String avatar,
                                @RequestParam(value = "province", required = false) String province,
                                @RequestParam(value = "district", required = false) String district,
                                @RequestParam(value = "ward", required = false) String ward,
                                @RequestParam(value = "streetAddress", required = false) String streetAddress,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        currentUser.setFullName(fullName != null ? fullName.trim() : currentUser.getFullName());
        currentUser.setPhone(phone != null ? phone.trim() : null);
        currentUser.setAvatar(avatar != null ? avatar.trim() : null);
        currentUser.setProvince(province != null ? province.trim() : null);
        currentUser.setDistrict(district != null ? district.trim() : null);
        currentUser.setWard(ward != null ? ward.trim() : null);
        currentUser.setStreetAddress(streetAddress != null ? streetAddress.trim() : null);

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

        redirectAttributes.addFlashAttribute("success", "Cập nhật hồ sơ cá nhân thành công!");
        return "redirect:/profile";
    }
}
