const add = document.querySelector("#add");
const reset = document.querySelector("#reset");
const amount = document.querySelector("#amount");
const progress = document.querySelector("#progress");
const bottle = document.querySelector(".bottle");
const liquid = document.querySelector(".liquid");
const format = new Intl.NumberFormat("ar-EG");
let filled = 0;

function render() {
  amount.value = format.format(filled);
  progress.value = filled;
  bottle.style.setProperty("--fill", `${filled / 1000}`);
  add.disabled = filled === 1000;
  reset.disabled = filled === 0;
}

add.addEventListener("click", () => {
  filled = Math.min(1000, filled + 250);
  render();
  if (!matchMedia("(prefers-reduced-motion: reduce)").matches) {
    liquid.getAnimations().forEach((animation) => animation.cancel());
    liquid.animate(
      [{ rotate: "-3deg" }, { rotate: "4deg" }, { rotate: "-1deg" }, { rotate: "0deg" }],
      { duration: 610, easing: "ease-out" }
    );
  }
});
reset.addEventListener("click", () => {
  filled = 0;
  render();
  add.focus();
});

const motionPreview = document.querySelector(".motion-preview");
const recording = motionPreview.querySelector("img");
motionPreview.addEventListener("toggle", () => {
  if (motionPreview.open) recording.src = recording.dataset.motion;
  else recording.removeAttribute("src");
});
