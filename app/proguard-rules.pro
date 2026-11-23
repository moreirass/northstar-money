# Room, Compose, WorkManager e ML Kit fornecem regras consumer próprias.
# Os serializers atuais têm referências explícitas geradas pelo compilador Kotlin.
# O núcleo Java não usa reflexão; não exige regras keep abrangentes.
-keepattributes Signature,InnerClasses,EnclosingMethod
