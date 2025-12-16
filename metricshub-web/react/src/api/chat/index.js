/**
 * Chat API for streaming AI chat responses
 */
class ChatApi {
	/**
	 * Stream chat response using Server-Sent Events (SSE)
	 * @param {Object} request - Chat request object
	 * @param {string} request.message - The user's message
	 * @param {Array<{role: string, content: string}>} request.history - Chat history
	 * @param {Function} onChunk - Callback for each chunk received
	 * @param {Function} onDone - Callback when stream completes
	 * @param {Function} onError - Callback for errors
	 * @returns {Promise<AbortController>} AbortController to cancel the request
	 */
	streamChat(request, { onChunk, onDone, onError }) {
		const abortController = new AbortController();

		// Use fetch for SSE streaming (EventSource doesn't support POST)
		fetch("/api/chat/stream", {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
			},
			credentials: "include", // Include cookies for authentication
			body: JSON.stringify({
				message: request.message,
				history: request.history || [],
			}),
			signal: abortController.signal,
		})
			.then(async (response) => {
				if (!response.ok) {
					const errorData = await response.json().catch(() => ({ error: "Unknown error" }));
					throw new Error(errorData.error || `HTTP ${response.status}`);
				}

				if (!response.body) {
					throw new Error("Response body is null");
				}

				const reader = response.body.getReader();
				const decoder = new TextDecoder();
				let buffer = "";
				let doneCallbackCalled = false;
				let streamDoneReceived = false;
				let errorReceived = false;
				let readerReleased = false;
				let readIterationCount = 0;
				const MAX_READ_ITERATIONS = 100000; // Safety guard against infinite loops

				const processEvent = (eventText) => {
					// Parse SSE event format:
					// event: <event-name>
					// data: <data>
					// data: <more-data> (multiple data lines are concatenated)
					const lines = eventText.split("\n");
					let eventName = null;
					const dataLines = [];

					for (let i = 0; i < lines.length; i++) {
						const line = lines[i];
						// Skip completely empty lines (but preserve lines that are just whitespace in data)
						if (line.length === 0) continue;

						if (line.startsWith("event:")) {
							// Remove "event:" prefix and trim
							eventName = line.slice(6).trim();
						} else if (line.startsWith("data:")) {
							// Remove "data:" prefix (exactly 5 characters: "data:")
							// Preserve EVERYTHING after "data:" exactly as-is, including:
							// - Leading spaces (if Spring sends "data: value")
							// - All content exactly as received
							const dataValue = line.substring(5); // Use substring instead of slice for clarity
							// Collect all data lines (SSE spec: multiple data lines are concatenated with \n)
							// DO NOT trim or modify - preserve exact content
							dataLines.push(dataValue);
						}
					}

					// Concatenate all data lines with newlines (per SSE spec)
					// This preserves the exact content including all whitespace
					const eventData = dataLines.join("\n");

					// Handle the event
					if (eventName === "connected") {
						// Connection established, continue
						return false; // Don't stop reading
					} else if (eventName === "chunk") {
						// Always call onChunk, even if eventData is empty (to handle empty chunks)
						// Preserve exact content including all whitespace - do not modify eventData
						if (onChunk && eventName === "chunk") {
							// Pass the data exactly as parsed, preserving all whitespace
							onChunk(eventData);
						}
						return false; // Don't stop reading
					} else if (eventName === "done") {
						// Mark that we've received the done event
						streamDoneReceived = true;
						// Call onDone callback once, but only if no error was received
						if (onDone && !doneCallbackCalled && !errorReceived) {
							doneCallbackCalled = true;
							onDone();
						}
						// Continue reading to consume any remaining stream data, but we'll stop soon
						return false;
					} else if (eventName === "error") {
						// Mark that an error was received
						errorReceived = true;
						try {
							const errorObj = JSON.parse(eventData);
							if (onError) onError(new Error(errorObj.error || "Unknown error"));
						} catch {
							if (onError) onError(new Error(eventData || "Unknown error"));
						}
						// Mark done callback as called to prevent onDone from being called after error
						doneCallbackCalled = true;
						// Don't stop reading - continue to consume the stream
						return false;
					}
					return false; // Continue reading
				};

				const readChunk = () => {
					// Guard against infinite loops
					readIterationCount++;
					if (readIterationCount > MAX_READ_ITERATIONS) {
						console.error("Maximum read iterations exceeded, stopping stream processing");
						if (!readerReleased) {
							try {
								reader.releaseLock();
								readerReleased = true;
							} catch {
								// Reader may already be released, ignore
							}
						}
						if (!errorReceived && onError) {
							errorReceived = true;
							onError(new Error("Stream processing exceeded maximum iterations"));
						}
						return;
					}

					// Guard against reading from a released reader
					if (readerReleased) {
						return;
					}

					reader
						.read()
						.then(({ done, value }) => {
							if (done) {
								// Process any remaining buffer content
								if (buffer.trim()) {
									processEvent(buffer);
								}
								// Ensure onDone is called if we haven't received a "done" event or error
								// Don't call onDone if an error was already handled
								if (!doneCallbackCalled && onDone && !errorReceived) {
									doneCallbackCalled = true;
									onDone();
								}
								// Stream is fully consumed - release the reader
								if (!readerReleased) {
									try {
										reader.releaseLock();
										readerReleased = true;
									} catch {
										// Reader may already be released, ignore
									}
								}
								return;
							}

							// If we already received "done" event or error, don't process more chunks
							// Just continue reading to consume remaining stream data
							if (streamDoneReceived || errorReceived) {
								// Continue reading to drain the stream, but don't process events
								// Only continue if reader hasn't been released
								if (!readerReleased) {
									readChunk();
								}
								return;
							}

							// Decode the chunk and add to buffer
							buffer += decoder.decode(value, { stream: true });

							// Process complete SSE events (separated by \n\n)
							while (buffer.includes("\n\n")) {
								const eventEnd = buffer.indexOf("\n\n");
								const eventText = buffer.substring(0, eventEnd);
								buffer = buffer.substring(eventEnd + 2);

								// Process event
								processEvent(eventText);
							}

							// If we received a "done" event or error, stop reading and release the reader
							// The stream should be consumed by now
							if (streamDoneReceived || errorReceived) {
								if (!readerReleased) {
									try {
										reader.releaseLock();
										readerReleased = true;
									} catch {
										// Reader may already be released
									}
								}
								return;
							}

							// Continue reading until stream is done or we received "done" event
							// Only continue if reader hasn't been released
							if (!readerReleased) {
								readChunk();
							}
						})
						.catch((error) => {
							if (error.name === "AbortError") {
								// Request was cancelled, release the reader
								if (!readerReleased) {
									try {
										reader.releaseLock();
										readerReleased = true;
									} catch {
										// Reader may already be released
									}
								}
								return;
							}
							// On error, still try to consume remaining stream (with guard)
							// But limit retries to prevent infinite loops
							if (!readerReleased && readIterationCount < MAX_READ_ITERATIONS) {
								readChunk().catch(() => {
									// If reading fails, release the reader and call error handler
									if (!readerReleased) {
										try {
											reader.releaseLock();
											readerReleased = true;
										} catch {
											// Reader may already be released
										}
									}
									if (!errorReceived && onError) {
										errorReceived = true;
										onError(error);
									}
								});
							} else {
								// Max iterations reached or reader released, just handle the error
								if (!readerReleased) {
									try {
										reader.releaseLock();
										readerReleased = true;
									} catch {
										// Reader may already be released
									}
								}
								if (!errorReceived && onError) {
									errorReceived = true;
									onError(error);
								}
							}
						});
				};

				readChunk();
			})
			.catch((error) => {
				if (error.name === "AbortError") {
					// Request was cancelled, don't call onError
					return;
				}
				if (onError) onError(error);
			});

		return abortController;
	}
}

// Export a singleton instance of ChatApi
export const chatApi = new ChatApi();
