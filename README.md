# cap-and-send

## How To Run

When you run:
`
lein run \ 
      --capture-time-secs CTS  60 \              #Capture time seconds
      --clip-interval-ms CIMS  15000 \           #Clip interval ms
      --fps FPS                10 \              #Frames per second
      --frame-dir FD           frames-dir \      #Frames dir
      --clip-dir CD            clips-dir \       #Clips dir
      --s3-upload-dir S3D      s3-dir \          #S3 upload dir
      --motion-dir MD          motion-dir \      #Motion dir
      --s3-bucket S3B          fake-s3-bucket \  #S3 bucket
      --s3-key S3K             fake-s3-key       #S3 key
`

 - capture frames for `60` seconds
 - every `15000` ms, create a video clip from the frames accumulated since last clip generation
 - at `10` frames per second
 - with frames saved in `frames-dir` (removed as clips are generated)
 - with clips created in `clips-dir`
 - and saved in `s3-dir`
 - with motion output saved in `motion-dir`
 - before being uploaded to `s3-bucket`/`s3-key`
## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
