
void setup() {
	Serial.begin(57600);
	Serial.setTimeout(5);
}

char inbuf[1024];

void loop() {
	// Wait for an incoming value, then send it back. Expected that everything ends in \r
	int readin = Serial.readBytesUntil('\r', inbuf, 1024);
	
	if (readin > 0) {
		Serial.write((uint8_t*) inbuf, readin);
		Serial.println("");
	}
}
