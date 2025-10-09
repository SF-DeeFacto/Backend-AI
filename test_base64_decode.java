import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class test_base64_decode {
    public static void main(String[] args) {
        // Base64 string from the log
        String base64Text = "IyDthYzsiqTtirgg66as7Y+s7Yq4IOyalOyVvSAoMjAyNS0wOC0yNyAxMjoyODozNikKCuyLnOyKpO2FnCDsoITrsJjsnZgg7IOB7YOc64qUIOyWke2YuO2VnCDqsoPsnLzroZwg7ZmV7J2465CY7JeY7Iq164uI64ukLiDso7zsmpQg7ISx64qlIOyngO2RnOuTpOydtCDrqqntkZzsuZjrpbwg64us7ISx7ZWY6rOgIOyeiOycvOupsCwg7J2867aAIOuzkeuqqSDtmITsg4HsnbQg67Cc6rKo65CY7JeI7Jy864KYIOqwnOyEoCDsobDsuZjrpbwg7Ya17ZW0IO2VtOqysO2VoCDsiJgg7J6I7J2EIOqyg+ycvOuhnCDrs7TsnoXri4jri6QuIO2Wpe2bhCDsp4Dsho3soIHsnbgg66qo64uI7YSw66eB6rO8IOy1nOqgge2ZlCDsnpHsl4XsnbQg7ZWE7JqU7ZWgIOqyg+ycvOuhnCDtjJDri6jrkKnri4jri6Qu";

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Text);
            String decodedText = new String(decodedBytes, StandardCharsets.UTF_8);
            System.out.println("Decoded text:");
            System.out.println(decodedText);
        } catch (Exception e) {
            System.out.println("Decoding failed: " + e.getMessage());
        }
    }
}
