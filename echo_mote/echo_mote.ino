
void setup() {
	Serial.begin(57600);
	Serial.setTimeout(5);
}

int bufidx = 0;
char inbuf[1024];

void loop() {
	bufidx = 0;
	
	int more = 1;
	while (more) {
		// Wait for the next byte
		int readin = Serial.readBytes(inbuf+bufidx, 1);
		if (readin > 0) {
			// We read in a byte
			if (inbuf[bufidx] == '\r') {
				// Do NOT keep this character in the buffer
				inbuf[bufidx] = '\0';
				--bufidx;
				// expect \n next
			} else if (inbuf[bufidx] == '\n') {
				// Do NOT keep this character in the buffer
				inbuf[bufidx] = '\0';
				--bufidx;
				
				// This is the last expected byte
				more = 0;
			} else {
				// That byte was just placed in the buffer. Sooo cool! Move on, waiting for more
			}
			
			// Move to the next spot
			++bufidx;
		} else {
			// Defer until the next loop() iteration
			more = 0;
		}
	}
	
	if (bufidx > 0) {
		// Then we have read in some bytes, and bufidx is our length (since it points to the next free spot)
		
		// Add \r\n to the end, then transmit it
		inbuf[bufidx] = '\r';
		++bufidx;
		inbuf[bufidx] = '\n';
		++bufidx;
		
		// Send the received data, ALWAYS ending with \r\n, regardless of what ending they sent
		Serial.write((uint8_t*) inbuf, bufidx);
	}
}
