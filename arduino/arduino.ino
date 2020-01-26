#include "Parser.h"

bool leftChannel[9][16] =
{
  {true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false},
  {true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false},
  {true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false},
  {true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false},
  {true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false},
  {true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false},
  {true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false},
  {true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false},
  {true, true, true, true, true, true, true, true, false, false, false, false, false, false, false, false}
};

bool rightChannel[9][16] =
{
  {false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true},
  {false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true},
  {false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true},
  {false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true},
  {false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true},
  {false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true},
  {false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true},
  {false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true},
  {false, false, false, false, false, false, false, false, true, true, true, true, true, true, true, true}
};

bool topChannel[9][16] =
{
  {true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true},
  {true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true},
  {true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true},
  {true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true},
  {true, false, true, false, true, false, true, false, true, false, true, false, true, false, true, false},
  {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
  {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
  {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
  {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false}
};

bool bottomChannel[9][16] =
{
  {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
  {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
  {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
  {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false},
  {false, true, false, true, false, true, false, true, false, true, false, true, false, true, false, true},
  {true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true},
  {true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true},
  {true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true},
  {true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true}
};

uint8_t pinMap[] =
{
  2,
  3,
  4,
  5,
  6,
  7,
  8,
  9,
  10,
  14,
  20,
  21,
  22,
  23,
  29,
  30,
  35,
  36,
  37,
  38
};

uint8_t ledMap[CHANNEL_COUNT][SUBCHANNEL_COUNT] =
{
  // channel 0
  {
    pinMap[15],   // Channel 0, red is at PWM 4
    pinMap[16],   // Channel 0, green is at PWM 3
    pinMap[17],   // Channel 0, blue is at PWM 2
    pinMap[18],   // Channel 0, cold white is at PWM 1
    pinMap[19]    // Channel 0, warm white is at PWM 0
  },
  // channel 1
  {
    pinMap[9],    // Channel 1, red is at PWM 11
    pinMap[10],   // Channel 1, green is at PWM 10
    pinMap[11],   // Channel 1, blue is at PWM 9
    pinMap[12],   // Channel 1, cold white is at PWM 8
    pinMap[13],   // Channel 1, warm white is at PWM 7
  },
  // channel 2
  {
    pinMap[0],  // Channel 2, red is at PWM 23
    pinMap[1],  // Channel 2, green is at PWM 22
    pinMap[2],  // Channel 2, blue is at PWM 21
    pinMap[3],  // Channel 2, cold white is at PWM 20
    pinMap[4]   // Channel 2, warm white is at PWM 19
  },
  // channel 3
  {
    pinMap[5],  // Channel 3, red is at PWM 16
    pinMap[6],  // Channel 3, green is at PWM 15
    pinMap[7],  // Channel 3, blue is at PWM 14
    pinMap[8],  // Channel 3, cold white is at PWM 13
    pinMap[14]  // Channel 3, warm white is at PWM 12
  },
  
  

};

Parser parser;
uint16_t channels[CHANNEL_COUNT][SUBCHANNEL_COUNT];

void setup() {
  // put your setup code here, to run once:
  analogWriteResolution(16);

  for (int i = 0; i < CHANNEL_COUNT * SUBCHANNEL_COUNT; i++)
  {
    pinMode(i, OUTPUT);
    analogWriteFrequency(pinMap[i], 915.527);
  }

  parser.onAmbilightPacketReceived(handleAmbilightPacketReceived);
}

void loop() {
  while (Serial.available() > 0)
  {
    char c = Serial.read();

    // Serial.printf("Received 0x%02X\n", c);

    parser.parseByte(c);
  }
//  testAllChannels();
}

void handleAmbilightPacketReceived()
{
  // parseAmbilightData(parser.colorData, sizeof(parser.colorData));
  setChannelColor(0, topChannel, parser.colorData);
  setChannelColor(1, rightChannel, parser.colorData);
  setChannelColor(2, bottomChannel, parser.colorData);
  setChannelColor(3, leftChannel, parser.colorData);
}

// enum
// {
//   RECV_HEADER_0,
//   RECV_HEADER_1,
//   RECV_HEADER_2,
//   RECV_HEADER_3,
//   RECV_DATA_LENGTH_0,
//   RECV_DATA_LENGTH_1,
//   RECV_FUNCTION,
//   RECV_DATA
// };

// static uint8_t next_byte = RECV_HEADER_0;
// static uint16_t received_length = 0;
// static uint16_t data_length = 0;
// static uint16_t received_data_length = 0;

// void constructPacket(uint8_t b)
// {

//   switch (next_byte)
//   {
//     case RECV_HEADER_0:
//       if (b == HEADER_0)
//       {
//         received_length = 0;
//         receiveBuffer[received_length] = b;
//         received_length++;
//         next_byte = RECV_HEADER_1;

//         //      setLED(0, 0, 0, 65535);
//         //      tlc.write();
//         //      Serial.println("Found header byte 0");
//       }
//       else
//       {
//         next_byte = RECV_HEADER_0;
//         //      Serial.printf("Lost header byte(0x%02X)\n", b);
//       }
//       break;

//     case RECV_HEADER_1:
//       if (b == HEADER_1)
//       {
//         receiveBuffer[received_length] = b;
//         received_length++;
//         next_byte = RECV_HEADER_2;

//         //      setLED(1, 0, 0, 65535);
//         //      tlc.write();

//         //      Serial.println("Found header byte 1");
//       }
//       else
//       {
//         next_byte = RECV_HEADER_0;
//         //      Serial.printf("Lost header byte(0x%02X)\n", b);
//       }
//       break;

//     case RECV_HEADER_2:
//       if (b == HEADER_2)
//       {
//         receiveBuffer[received_length] = b;
//         received_length++;
//         next_byte = RECV_HEADER_3;

//         //      setLED(2, 0, 0, 65535);
//         //      tlc.write();

//         //      Serial.println("Found header byte 2");
//       }
//       else
//       {
//         next_byte = RECV_HEADER_0;
//         //      Serial.printf("Lost header byte(0x%02X)\n", b);
//       }
//       break;

//     case RECV_HEADER_3:
//       if (b == HEADER_3)
//       {
//         receiveBuffer[received_length] = b;
//         received_length++;
//         next_byte = RECV_DATA_LENGTH_0;

//         //      setLED(3, 0, 0, 65535);
//         //      tlc.write();

//         //      Serial.println("Header complete");
//         //      Serial.println();
//       }
//       else
//       {
//         next_byte = RECV_HEADER_0;
//         //      Serial.printf("Lost header byte(0x%02X)\n", b);
//       }
//       break;

//     case RECV_DATA_LENGTH_0:
//       receiveBuffer[received_length] = b;
//       received_length++;
//       next_byte = RECV_DATA_LENGTH_1;
//       break;

//     case RECV_DATA_LENGTH_1:
//       receiveBuffer[received_length] = b;
//       received_length++;

//       data_length = uint8Touint16(receiveBuffer[CMD_DATA_LENGTH], receiveBuffer[CMD_DATA_LENGTH + 1]);

//       next_byte = RECV_FUNCTION;

//       // Serial.print("Data length: ");
//       // Serial.println(data_length);
//       break;

//     case RECV_FUNCTION:

//       receiveBuffer[received_length] = b;
//       received_length++;

//       // setLED(0, 0, 65535, 0);
//       // setLED(1, 0, 65535, 0);
//       // setLED(2, 0, 65535, 0);
//       // setLED(3, 0, 65535, 0);
//       // tlc.write();

//       if (receiveBuffer[CMD_FUNCTION] == FUNCTION_START)
//       {
//         // Serial.println("FUNCTION_START");
//         // setLED(0, 0, 65535, 0);
//         // setLED(1, 0, 65535, 0);
//         // setLED(2, 0, 65535, 0);
//         // setLED(3, 0, 65535, 0);
//         // tlc.write();
//         //            isAmbilightOn = true;
//       }

//       if (receiveBuffer[CMD_FUNCTION] == FUNCTION_STOP)
//       {
//         // Serial.println("FUNCTION_START");
//         // setLED(0, 65535, 0, 0);
//         // setLED(1, 65535, 0, 0);
//         // setLED(2, 65535, 0, 0);
//         // setLED(3, 65535, 0, 0);
//         // tlc.write();
//         //            isAmbilightOn = false;
//       }

//       next_byte = RECV_DATA;
//       break;



//     case RECV_DATA:
//       receiveBuffer[received_length] = b;
//       received_length++;
//       received_data_length++;

//       if (received_data_length == data_length)
//       {
//         // Serial.printf("Received %d of %d bytes\n", received_data_length, data_length);

//         switch (receiveBuffer[CMD_FUNCTION])
//         {
//           case FUNCTION_START:
//             // Serial.println("FUNCTION_START");
//             //                 isAmbilightOn = true;
//             break;

//           case FUNCTION_DATA:
//             // Serial.println("FUNCTION_AMBILIGHT");
//             parseAmbilightData(receiveBuffer, sizeof(receiveBuffer));

//             // setLED(0, 0, 65535, 0);
//             // setLED(1, 0, 65535, 0);
//             // setLED(2, 0, 65535, 0);
//             // setLED(3, 0, 65535, 0);
//             // tlc.write();

//             setChannelColor(0, topChannel, colorData);
//             setChannelColor(1, rightChannel, colorData);
//             setChannelColor(2, bottomChannel, colorData);
//             setChannelColor(3, leftChannel, colorData);

//             updateLEDs();

//             break;

//           case FUNCTION_PAUSE:
//             // Serial.println("FUNCTION_PAUSE");
//             break;

//           case FUNCTION_STOP:
//             // Serial.println("FUNCTION_STOP");
//             //                 isAmbilightOn = false;
//             break;
//         }

//         next_byte = RECV_HEADER_0;
//         received_length = 0;
//         received_data_length = 0;

//         Serial.write(0xFF);
//       }
//       break;
//   }
// }

// void parseAmbilightData(uint8_t _colorData[], uint16_t length)
// {
//   if (length == AMBILIGHT_PACKET_LENGTH)
//   {
//     for (uint16_t y = 0; y < GRID_SEGMENTS_Y; y++)
//     {
//       for (uint16_t x = 0; x < GRID_SEGMENTS_X; x++)
//       {
//         uint16_t arrayPosition = ((GRID_SEGMENTS_X * y) + x) * 3 * 2 + CMD_DATA;

//         // Serial.printf("x: %d, y: %d pos: %d, value: %d \n", x, y, arrayPosition, _colorData[arrayPosition]);

//         colorData[x][y][0] = uint8Touint16(_colorData[arrayPosition + 0], _colorData[arrayPosition + 1]);
//         colorData[x][y][1] = uint8Touint16(_colorData[arrayPosition + 2], _colorData[arrayPosition + 3]);
//         colorData[x][y][2] = uint8Touint16(_colorData[arrayPosition + 4], _colorData[arrayPosition + 5]);
//       }
//     }

//     // Serial.printf("parsedRed %d\n", colorData[0][0][0]);
//     // Serial.printf("parsedGreen %d\n", colorData[0][0][1]);
//     // Serial.printf("parsedBlue %d\n", colorData[0][0][2]);

//     // printColorArray();
//   }
//   else
//   {
//     // Serial.printf("Ambilight data length incorrect, received %d, expected %d\n", length, AMBILIGHT_DATA_LENGTH);
//   }
// }

void setChannelColor(uint8_t channel, bool _enabledSegments[][16], uint16_t _colorData[][9][3])
{
  // Serial.println("Setting LEDs");

  uint32_t r = 0;
  uint32_t g = 0;
  uint32_t b = 0;
  uint16_t segmentCount = 0;

  for (uint8_t y = 0; y < GRID_SEGMENTS_Y; y++)
  {
    for (uint8_t x = 0; x < GRID_SEGMENTS_X; x++)
    {
      if (_enabledSegments[y][x])
      {
        segmentCount++;
        r += _colorData[x][y][0];
        g += _colorData[x][y][1];
        b += _colorData[x][y][2];
      }
    }
  }

  channels[channel][0] = (double)r / (double)segmentCount;
  channels[channel][1] = (double)g / (double)segmentCount;
  channels[channel][2] = (double)b / (double)segmentCount;
}

void updateLEDs()
{
  for (uint8_t i = 0; i < CHANNEL_COUNT; i++)
  {
    uint16_t r = (float)channels[i][0];
    uint16_t g = (float)channels[i][1];
    uint16_t b = (float)channels[i][2];

    for (uint8_t j = 0; j < SUBCHANNEL_COUNT; j++)
    {
      analogWrite(ledMap[i][j], channels[i][j]);
    }
  }

}

// uint16_t uint8Touint16(uint8_t high, uint8_t low)
// {
//   uint16_t number = low | high << 8;
//   return number;
// }

void testAllChannels()
{
  for (int c = 0; c < CHANNEL_COUNT; c++)
  {
    for (int sc = 0; sc < SUBCHANNEL_COUNT; sc++)
    {
      // First make everything 0
      for (int i = 0; i < CHANNEL_COUNT; i++)
      {
        for (int j = 0; j < SUBCHANNEL_COUNT; j++)
        {
          analogWrite(ledMap[i][j], 0);
        }
      }

      // Then write to the channel and subchannel we want to highlight
      analogWrite(ledMap[c][sc], 65535);
      delay(500);
    }
  }

}

void colorLoop()
{
  float s = (float)millis() / 1000.0;
  float val = 0.5 * sin(PI * s) + 0.5;
  float val2 = 0.5 * sin(PI * s * 1.3) + 0.5;
  float val3 = 0.5 * sin(PI * s * 1.6) + 0.5;
  float val4 = 0.5 * sin(PI * s * 1.9) + 0.5;
  float val5 = 0.5 * sin(PI * s * 2.2) + 0.5;
  uint16_t output = val * 65535;

  for (int i = 0; i < CHANNEL_COUNT; i++)
  {
    analogWrite(pinMap[(i * SUBCHANNEL_COUNT) + 0], val * 65535);
    analogWrite(pinMap[(i * SUBCHANNEL_COUNT) + 1], val2 * 65535);
    analogWrite(pinMap[(i * SUBCHANNEL_COUNT) + 2], val3 * 65535);
    analogWrite(pinMap[(i * SUBCHANNEL_COUNT) + 3], val4 * 65535);
    analogWrite(pinMap[(i * SUBCHANNEL_COUNT) + 4], val5 * 65535);
  }
}
