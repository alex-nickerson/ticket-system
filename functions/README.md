# functions

Azure Function(s) for the notification pipeline (milestone 9).

The API enqueues events (ticket assigned, SLA breached) onto an Azure Storage
Queue; a Java Azure Function here consumes them and sends email via Azure
Communication Services.

Empty for now — this placeholder exists because Git does not track empty
directories.
