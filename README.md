# Pokémon GO Rater
Android App to estimate a Pokémon level in Pokémon GO.

## Notice
This project has been discontinued, since there is a project in a more advanced development stage here: https://github.com/farkam135/GoIV
I decided to upload it to github anyway, in case someone is interested in the source code.

## Downloads
The apk is available on the [release page](https://github.com/KowKiller/PokemonGORater/releases).

## Third party libraries
This application requires OpenCV computer vision library to be installed on the device for image processing.
In case the [OpenCV Manager](https://play.google.com/store/apps/details?id=org.opencv.engine) is not installed on the device, the app will ask the user to install it and redirect to the play store market. 

## Usage
1. Start the app and click on "Start Pokémon GO Rater" button
2. You should see a PokéCaptor button on the upper-left corner of your screen. This button will stay on top of other apps.
3. Open the Pokémon GO app and go into the details of the Pokémon of which you want to estimate the level, as shown in the image:
  * ![Pokémon Captor](http://i.imgur.com/2wHpIwhl.png)
4. Tap on the PokéCaptor button to show a menu and select Capture Pokémon Data (App will ask for screen capture permissions)
5. The Analysis Screen should appear. Select your trainer level by clicking on the "Change" button in the top right part of the screen. The windows should display the estimated Pokémon level and a debug image as well, to double check if the arc was detected correctly.
  * ![Analysis Screen](http://i.imgur.com/clK4K6Rl.png)
6. Optionally, you can tap on the "Upload Feedback Data" button to upload on my server the captured screen with your level and the estimated pokémon level info, to improve the arc detection. (This is probably not gonna happen, see [notice](#notice))

# Troubleshooting
Project is still in alpha, so level estimation sometimes may fail. Especially when the pokémon arc is partially hidden by the Pokémon or other floating objects.
Or when the Pokémon background is too bright.
In case this happens, try to move the Pokémon and trigger another capture.
