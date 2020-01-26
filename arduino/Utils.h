
uint16_t uint8Touint16(uint8_t high, uint8_t low)
{
  uint16_t number = low | high << 8;
  return number;
}