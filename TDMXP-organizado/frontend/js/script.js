const yearElement = document.querySelector("#year");
const welcomeButton = document.querySelector("#welcomeButton");
const messageElement = document.querySelector("#message");

if (yearElement) {
  yearElement.textContent = new Date().getFullYear();
}

if (welcomeButton && messageElement) {
  welcomeButton.addEventListener("click", () => {
    messageElement.textContent = "¡JavaScript está funcionando correctamente!";
  });
}
