# cap-and-send

## How To Run

When you run:
`
lein run 120 5000 10 frame-dir clip-dir s3-dir s3-bucket s3-key
`

 - capture frames for `120` seconds
 - every `5000` ms, create a video clip from the frames accumulated since last clip generation
 - at `10` frames per second
 - with frames saved in `frame-dir` (removed as clips are generated)
 - with clips created in `clip-dir`
 - and saved in `s3-dir`
 - before being uploaded to `s3-bucket`/`s3-key`
## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
