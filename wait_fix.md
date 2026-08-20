Not with pure inline styles — `@keyframes` can't live in a `style` attribute. But you have two options that avoid touching a shared stylesheet.

## Option 1 — a `<style>` tag in the component

Still CSS, but self-contained. Drop it right above the `<h3>`:

```tsx
{isLoading &&
<>
    <style>{`
        @keyframes mv-dots { 0%,20%{content:""} 40%{content:"."} 60%{content:".."} 80%,100%{content:"..."} }
        .mv-loading-dots::after { content:""; animation: mv-dots 1.2s infinite; display:inline-block; width:1.5em; text-align:left; }
    `}</style>
    <h3>Fetching data from the server, please wait<span className="mv-loading-dots" /></h3>
}
```

The `mv-` prefix keeps the keyframe name from colliding with anything else.

## Option 2 — pure React, no CSS at all

```tsx
const [dots, setDots] = useState("");

useEffect(() => {
    if (!isLoading) return;
    const id = setInterval(() => setDots(d => (d.length >= 3 ? "" : d + ".")), 400);
    return () => clearInterval(id);
}, [isLoading]);
```

```tsx
{isLoading &&
    <h3>Fetching data from the server, please wait{dots}</h3>
}
```

Fully inline, no stylesheet, easy to read.

## Which one

**Option 1**, and it's not close. Option 2 fires a `setState` every 400ms, and each one re-renders `ModuleViewTable` — a component with dozens of hooks and a DataTable. During a data load, when the main thread is already busy, you'd be adding work to exactly the thing you just spent a day making fast.

CSS animations run off the main thread entirely. The `<style>` tag keeps it local without that cost.
