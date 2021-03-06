# nbsproxy

Android app that exists only to send and receive small data messages
for other apps using "narrow band sockets" (called "data sms" on
Android)

Background: 

* "Narrow Band Sockets" (NBS) are an old but still useful way of
  sending tiny amounts of data (~<=140 bytes) on GSM networks. The
  original text messaging features on pre-smart-phones used them.

* Maybe because of this, Google equates them with the world of
  user-visible SMS, providing access via the method
  SmsManager.sendDataMessage()

* And so the SMS_SEND and SMS_RECEIVE permissions are required to use
  NBS

* The Google Play Store is banning apps' use of SMS_SEND and
  SMS_RECEIVE *unless* it's required for their core
  functionality. This means that you can no longer use NBS to transmit
  the moves of a game, say, in versions of your app distributed
  through the Play Store.

Thus NBSProxy has as its core and only function the transmission of
data using NBS. Sending is done with
SmsManager.sendDataMessage(). Receiving is done via a
BroadcastReceiver it registers in its AndroidManifest.xml file.

It's a proof-of-concept right now: it works to send between any two
client apps, and has a rough UI. It warns about being on the wrong
type of phone, tries to get the permissions it needs, and does a bit
of usage tracking. It works quite well in fact once it's up and
running, but there are some glitches in the immediate post-install
experience that need to be fixed before it's ready for release.

It's meant to be ridiculously small and simple so that anybody who
knows a bit of Android can verify in minutes that it's not doing
anything nasty. Because there's a lot of concern these days about
anything with "SMS" in its name....

The javadoc comments in NBSProxy.java describe how to use this app
from your app. Or should.

If you're interested in using it, or in helping with development,
please be in touch!

License is GPL v2.

Thanks,

--Eric
eehouse@eehouse.org
