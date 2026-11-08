package com.tuyenmai.openkey.core

object OpenKeyL10n {

    enum class Language {
        VI,
        EN
    }

    interface SectionStrings {
        fun getStrings(lang: Language): Map<String, String>
    }

    object Onboarding : SectionStrings {
        private val vi = mapOf(
            "enableKeyboardTitle" to "Bật bàn phím OpenKey",
            "enableKeyboardDesc" to "Cho phép OpenKey trong danh sách bàn phím hệ thống",
            "selectKeyboardTitle" to "Chọn phương thức nhập",
            "selectKeyboardDesc" to "Đặt OpenKey làm bàn phím mặc định",
            "onboardingComplete" to "Sẵn sàng sử dụng!",
            "testKeyboardHint" to "Chạm vào đây để gõ thử..."
        )

        private val en = mapOf(
            "enableKeyboardTitle" to "Enable OpenKey Keyboard",
            "enableKeyboardDesc" to "Allow OpenKey in system keyboard settings",
            "selectKeyboardTitle" to "Select Input Method",
            "selectKeyboardDesc" to "Set OpenKey as your default keyboard",
            "onboardingComplete" to "Ready to use!",
            "testKeyboardHint" to "Tap here to test typing..."
        )

        override fun getStrings(lang: Language): Map<String, String> = if (lang == Language.VI) vi else en
    }

    object Ergonomics : SectionStrings {
        private val vi = mapOf(
            "sectionTitle" to "Kích thước & Công thái học màn hình lớn",
            "heightScaleTitle" to "Tỷ lệ chiều cao bàn phím",
            "bottomChinTitle" to "Đệm dưới đáy (khoảng cách điều hướng)",
            "oneHandedTitle" to "Chế độ một tay",
            "oneHandedOff" to "Tắt",
            "oneHandedLeft" to "Bên trái",
            "oneHandedRight" to "Bên phải",
            "numberRowTitle" to "Hàng phím số chuyên dụng",
            "hapticsTitle" to "Rung phản hồi khi chạm",
            "popupPreviewTitle" to "Bóng xem trước phím gõ"
        )

        private val en = mapOf(
            "sectionTitle" to "Large-Screen Size & Ergonomics",
            "heightScaleTitle" to "Keyboard Height Scale",
            "bottomChinTitle" to "Bottom Chin Padding",
            "oneHandedTitle" to "One-Handed Mode",
            "oneHandedOff" to "Off",
            "oneHandedLeft" to "Left",
            "oneHandedRight" to "Right",
            "numberRowTitle" to "Dedicated Number Row",
            "hapticsTitle" to "Haptic Feedback",
            "popupPreviewTitle" to "Key Popup Preview"
        )

        override fun getStrings(lang: Language): Map<String, String> = if (lang == Language.VI) vi else en
    }

    object Engine : SectionStrings {
        private val vi = mapOf(
            "sectionTitle" to "Bộ gõ tiếng Việt & Song ngữ",
            "inputMethodTitle" to "Kiểu gõ",
            "telexTitle" to "Telex",
            "vniTitle" to "VNI",
            "modernOrthographyTitle" to "Đặt dấu theo chuẩn mới (oà, uý)",
            "checkSpellingTitle" to "Kiểm tra chính tả",
            "restoreWrongSpellingTitle" to "Tự động khôi phục từ sai chính tả",
            "bilingualTitle" to "Khôi phục từ tiếng Anh tự động",
            "smartSwitchTitle" to "Ghi nhớ ngôn ngữ từng ứng dụng (Smart Switch)"
        )

        private val en = mapOf(
            "sectionTitle" to "Vietnamese Engine & Bilingual",
            "inputMethodTitle" to "Input Method",
            "telexTitle" to "Telex",
            "vniTitle" to "VNI",
            "modernOrthographyTitle" to "Modern Orthography (oà, uý)",
            "checkSpellingTitle" to "Check Spelling",
            "restoreWrongSpellingTitle" to "Auto-restore Wrong Spelling",
            "bilingualTitle" to "Bilingual English Auto-restore",
            "smartSwitchTitle" to "Per-app Language Memory (Smart Switch)"
        )

        override fun getStrings(lang: Language): Map<String, String> = if (lang == Language.VI) vi else en
    }

    object AutoWording : SectionStrings {
        private val vi = mapOf(
            "sectionTitle" to "Gợi ý từ & Viết tắt",
            "autoWordingMasterTitle" to "Kích hoạt tính năng gợi ý thông minh",
            "grammarRepairTitle" to "Tự động sửa đảo chữ (tineg -> tiếng)",
            "shorthandTitle" to "Cho phép mở rộng gõ tắt",
            "candidateStripTitle" to "Hiển thị thanh từ gợi ý",
            "customShorthandEditorTitle" to "Danh sách viết tắt cá nhân",
            "resetFrequenciesTitle" to "Đặt lại tần suất học từ",
            "resetFrequenciesSuccess" to "Đã đặt lại tần suất học từ thành công"
        )

        private val en = mapOf(
            "sectionTitle" to "Auto Wording & Shorthands",
            "autoWordingMasterTitle" to "Enable Smart Wording",
            "grammarRepairTitle" to "Repair Cluster Transposition",
            "shorthandTitle" to "Enable Shorthand Expansion",
            "candidateStripTitle" to "Candidate Suggestion Strip",
            "customShorthandEditorTitle" to "Custom Shorthands",
            "resetFrequenciesTitle" to "Reset Learned Word Frequencies",
            "resetFrequenciesSuccess" to "Learned word frequencies reset successfully"
        )

        override fun getStrings(lang: Language): Map<String, String> = if (lang == Language.VI) vi else en
    }

    object Debug : SectionStrings {
        fun sectionTitle(lang: Language): String = if (lang == Language.VI) "Gỡ lỗi & Chẩn đoán" else "Debug & Diagnostics"
        fun toggleTitle(lang: Language): String = if (lang == Language.VI) "Chế độ Gỡ lỗi (Debug Mode)" else "Debug Mode"
        fun toggleSubtitle(lang: Language): String = if (lang == Language.VI) "Ghi nhật ký chi tiết vào tệp để phân tích sự cố" else "Log detailed diagnostic events to file"
        fun copyLogs(lang: Language): String = if (lang == Language.VI) "Sao chép nhật ký" else "Copy Logs"
        fun clearLogs(lang: Language): String = if (lang == Language.VI) "Xóa nhật ký" else "Clear Log"
        fun emptyLogs(lang: Language): String = if (lang == Language.VI) "Chưa có nhật ký nào được ghi." else "No diagnostic logs recorded yet."

        private val vi = mapOf(
            "sectionTitle" to sectionTitle(Language.VI),
            "toggleTitle" to toggleTitle(Language.VI),
            "toggleSubtitle" to toggleSubtitle(Language.VI),
            "copyLogs" to copyLogs(Language.VI),
            "clearLogs" to clearLogs(Language.VI),
            "emptyLogs" to emptyLogs(Language.VI)
        )

        private val en = mapOf(
            "sectionTitle" to sectionTitle(Language.EN),
            "toggleTitle" to toggleTitle(Language.EN),
            "toggleSubtitle" to toggleSubtitle(Language.EN),
            "copyLogs" to copyLogs(Language.EN),
            "clearLogs" to clearLogs(Language.EN),
            "emptyLogs" to emptyLogs(Language.EN)
        )

        override fun getStrings(lang: Language): Map<String, String> = if (lang == Language.VI) vi else en
    }

    object About : SectionStrings {
        private val vi = mapOf(
            "sectionTitle" to "Thông tin ứng dụng",
            "appName" to "OpenKey Android",
            "appVersion" to "Phiên bản 2.0.0",
            "developerCredit" to "Phát triển bởi Antigravity — Gemini 4 Argon",
            "privacyNotice" to "100% Ngoại tuyến • Không yêu cầu quyền Internet • Bảo mật tuyệt đối"
        )

        private val en = mapOf(
            "sectionTitle" to "About",
            "appName" to "OpenKey Android",
            "appVersion" to "Version 2.0.0",
            "developerCredit" to "Engineered by Antigravity — Gemini 4 Argon",
            "privacyNotice" to "100% Offline • Zero Internet Permission • Strict Privacy"
        )

        override fun getStrings(lang: Language): Map<String, String> = if (lang == Language.VI) vi else en
    }
}
