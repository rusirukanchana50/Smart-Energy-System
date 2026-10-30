/**
 * auth.js
 * Handles show/hide password toggle, client-side validation, AJAX login,
 * CSRF token persistence, and CSRF-protected logout.
 */

document.addEventListener("DOMContentLoaded", () => {
  const toggleBtn = document.getElementById("togglePasswordBtn");
  const passwordInput = document.getElementById("password");
  const eyeIcon = document.getElementById("eyeIcon");
  const eyeOffIcon = document.getElementById("eyeOffIcon");
  const loginForm = document.getElementById("loginForm");
  const loginSubmitBtn = document.getElementById("loginSubmitBtn");
  const emailInput = document.getElementById("email");
  const clientAlert = document.getElementById("clientAlert");

  // Show/Hide Password Toggle
  if (toggleBtn && passwordInput) {
    toggleBtn.addEventListener("click", () => {
      const isPassword = passwordInput.type === "password";
      passwordInput.type = isPassword ? "text" : "password";

      if (eyeIcon && eyeOffIcon) {
        if (isPassword) {
          eyeIcon.style.display = "none";
          eyeOffIcon.style.display = "block";
          toggleBtn.setAttribute("aria-label", "Hide password");
        } else {
          eyeIcon.style.display = "block";
          eyeOffIcon.style.display = "none";
          toggleBtn.setAttribute("aria-label", "Show password");
        }
      }
    });
  }

  // Hide client alert on user typing
  if (emailInput) {
    emailInput.addEventListener("input", () => {
      if (clientAlert) clientAlert.style.display = "none";
    });
  }
  if (passwordInput) {
    passwordInput.addEventListener("input", () => {
      if (clientAlert) clientAlert.style.display = "none";
    });
  }

  // Display URL query param errors (e.g. ?error=...)
  const urlParams = new URLSearchParams(window.location.search);
  const errorParam = urlParams.get("error");
  if (errorParam && clientAlert) {
    clientAlert.textContent = decodeURIComponent(errorParam);
    clientAlert.style.display = "flex";
  }

  // Form submission with fetch
  if (loginForm) {
    loginForm.addEventListener("submit", async (e) => {
      e.preventDefault();

      const email = emailInput ? emailInput.value.trim() : "";
      const password = passwordInput ? passwordInput.value : "";

      if (!email || !password) {
        showClientError("Please enter both email and password.");
        return;
      }

      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(email)) {
        showClientError("Please enter a valid university email address.");
        return;
      }

      // Visual feedback on submit button
      const originalBtnHtml = loginSubmitBtn ? loginSubmitBtn.innerHTML : "";
      if (loginSubmitBtn) {
        loginSubmitBtn.disabled = true;
        loginSubmitBtn.innerHTML = `
          <svg class="spinner" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="animation: spin 1s linear infinite;">
            <line x1="12" y1="2" x2="12" y2="6"></line>
            <line x1="12" y1="18" x2="12" y2="22"></line>
            <line x1="4.93" y1="4.93" x2="7.76" y2="7.76"></line>
            <line x1="16.24" y1="16.24" x2="19.07" y2="19.07"></line>
            <line x1="2" y1="12" x2="6" y2="12"></line>
            <line x1="18" y1="12" x2="22" y2="12"></line>
            <line x1="4.93" y1="19.07" x2="7.76" y2="16.24"></line>
            <line x1="16.24" y1="7.76" x2="19.07" y2="4.93"></line>
          </svg>
          Signing in...
        `;
      }

      try {
        const response = await fetch("/api/auth/login", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Accept": "application/json"
          },
          body: JSON.stringify({ email, password })
        });

        const data = await response.json().catch(() => ({}));

        if (!response.ok) {
          showClientError(data.message || "Invalid email or password. Please try again.");
          if (loginSubmitBtn) {
            loginSubmitBtn.disabled = false;
            loginSubmitBtn.innerHTML = originalBtnHtml;
          }
          return;
        }

        // Store CSRF token in sessionStorage for subsequent state-changing requests
        if (data.csrf_token) {
          sessionStorage.setItem("csrf_token", data.csrf_token);
        }

        // Redirect to dashboard
        window.location.href = data.redirect || "/dashboard";

      } catch (err) {
        showClientError("Network or server connection error. Please try again.");
        if (loginSubmitBtn) {
          loginSubmitBtn.disabled = false;
          loginSubmitBtn.innerHTML = originalBtnHtml;
        }
      }
    });
  }

  function showClientError(message) {
    if (clientAlert) {
      clientAlert.textContent = message;
      clientAlert.style.display = "flex";
    } else {
      alert(message);
    }
  }
});

// Global CSRF-Protected Logout Helper
window.logoutUser = async function() {
  const csrfToken = sessionStorage.getItem("csrf_token");
  try {
    await fetch("/api/auth/logout", {
      method: "POST",
      headers: {
        "X-CSRF-TOKEN": csrfToken || "",
        "Accept": "application/json"
      }
    });
  } catch (e) {
    console.error("Logout request error:", e);
  } finally {
    sessionStorage.removeItem("csrf_token");
    window.location.href = "/login";
  }
};
