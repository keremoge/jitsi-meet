package org.webrtc;

import java.util.Locale;

public class SessionDescription {
   public final SessionDescription.Type type;
   public final String description;

   @CalledByNative
   public SessionDescription(SessionDescription.Type type, String description) {
      this.type = type;
      this.description = description;
   }

   @CalledByNative
   String getDescription() {
      return this.description;
   }

   @CalledByNative
   String getTypeInCanonicalForm() {
      return this.type.canonicalForm();
   }

   public static enum Type {
      OFFER,
      PRANSWER,
      ANSWER,
      ROLLBACK;

      public String canonicalForm() {
         return this.name().toLowerCase(Locale.US);
      }

      @CalledByNative("Type")
      public static SessionDescription.Type fromCanonicalForm(String canonical) {
         return (SessionDescription.Type)valueOf(SessionDescription.Type.class, canonical.toUpperCase(Locale.US));
      }

      // $FF: synthetic method
      private static SessionDescription.Type[] $values() {
         return new SessionDescription.Type[]{OFFER, PRANSWER, ANSWER, ROLLBACK};
      }
   }
}
