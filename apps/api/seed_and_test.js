const fs = require('fs');

const BASE_URL = 'http://localhost:8080/api/v1';

async function req(method, endpoint, body = null, token = null) {
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    
    let options = { method, headers };
    
    if (body instanceof FormData) {
        options.body = body;
    } else if (body) {
        headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }

    const res = await fetch(`${BASE_URL}${endpoint}`, options);
    if (!res.ok) {
        const text = await res.text();
        throw new Error(`HTTP ${res.status} on ${method} ${endpoint}: ${text}`);
    }
    
    if (res.status === 204) return null;
    return res.json();
}

async function uploadMedia(token, entityType, filename, content) {
    const formData = new FormData();
    formData.append('file', new Blob([content], { type: 'text/plain' }), filename);
    
    const headers = { 'Authorization': `Bearer ${token}` };
    const res = await fetch(`${BASE_URL}/media/${entityType}/upload`, {
        method: 'POST',
        headers,
        body: formData
    });
    if (!res.ok) throw new Error(`Upload failed: ${await res.text()}`);
    return res.json();
}

async function run() {
    try {
        console.log("1. Authenticating as Admin...");
        const adminAuth = await req('POST', '/auth', { username: 'admin@meetstudent.com', password: 'password' });
        const adminToken = adminAuth.accessToken;

        console.log("2. Fetching Roles...");
        const roles = await req('GET', '/roles', null, adminToken);
        const studentRole = roles.find(r => r.name === 'ROLE_STUDENT');
        const expertRole = roles.find(r => r.name === 'ROLE_EXPERT');

        console.log("3. Creating 5 Tags...");
        const tagNames = ["ENGINEERING", "BUSINESS", "ART", "MEDICAL", "SCIENCE"];
        const tags = [];
        for (let name of tagNames) {
            try {
                const tag = await req('POST', '/tags', { name }, adminToken);
                tags.push(tag);
            } catch (e) {
                if (e.message.includes('400')) {
                   const tag = await req('GET', `/tags/name/${name}`, null, adminToken);
                   tags.push(tag);
                } else throw e;
            }
        }

        console.log("4. Creating 10 Schools...");
        const schools = [];
        for (let i = 1; i <= 10; i++) {
            const school = await req('POST', '/schools', {
                name: `Test School ${i}`,
                code: `SCH${i.toString().padStart(2, '0')}`,
                address: { city: `City ${i}`, country: "Testland" },
                tags: [tags[i % tags.length], tags[(i+1) % tags.length]]
            }, adminToken);
            schools.push(school);
        }

        console.log("5. Creating 100 Programs (10 per school)...");
        const programs = [];
        let programCounter = 1;
        for (let s of schools) {
            for (let i = 1; i <= 10; i++) {
                const prog = await req('POST', '/programs', {
                    name: `Program ${i} of ${s.name}`,
                    code: `P${programCounter.toString().padStart(4, '0')}`,
                    duration: Math.floor(Math.random() * 5) + 1,
                    schoolId: s.id
                }, adminToken);
                programs.push(prog);
                programCounter++;
            }
        }

        console.log("6. Creating 500 Courses (5 per program)...");
        const courses = [];
        let courseCounter = 1;
        for (let p of programs) {
            for (let i = 1; i <= 5; i++) {
                const c = await req('POST', '/courses', {
                    name: `Course ${courseCounter}`,
                    code: `C${courseCounter.toString().padStart(4, '0')}`,
                    programId: p.id
                }, adminToken);
                courses.push(c);
                courseCounter++;
                if (courseCounter % 100 === 0) console.log(`   ...created ${courseCounter} courses`);
            }
        }

        console.log("7. Registering Student and Expert...");
        const ts = Date.now();
        const student = await req('POST', '/users', {
            firstname: "Sam", lastname: "Student", email: `student${ts}@test.com`, password: "password", confirmedPassword: "password", role: { id: studentRole.id }
        });
        const expert = await req('POST', '/users', {
            firstname: "Evan", lastname: "Expert", email: `expert${ts}@test.com`, password: "password", confirmedPassword: "password", role: { id: expertRole.id }
        });

        console.log("8. Authenticating new users...");
        const studentAuth = await req('POST', '/auth', { username: student.email, password: 'password' });
        const studentToken = studentAuth.accessToken;
        
        const expertAuth = await req('POST', '/auth', { username: expert.email, password: 'password' });
        const expertToken = expertAuth.accessToken;

        console.log("9. Uploading Media (Student)...");
        const diploma = await uploadMedia(studentToken, 'users', 'diploma.txt', 'Dummy Diploma Content');
        const video = await uploadMedia(studentToken, 'users', 'video.txt', 'Dummy Video Content');

        console.log("10. Updating Profile & Wishlist (Student)...");
        await req('PATCH', `/users/${student.id}`, {
            diplomas: [diploma.url],
            presentationVideoUrl: video.url
        }, studentToken);
        await req('POST', `/users/${student.id}/wishlist/${schools[0].id}`, null, studentToken);

        console.log("11. Uploading Media & Updating Profile (Expert)...");
        const cert = await uploadMedia(expertToken, 'users', 'cert.txt', 'Dummy Cert Content');
        await req('PATCH', `/users/${expert.id}`, {
            certificates: [cert.url]
        }, expertToken);

        console.log("12. Rating Entities...");
        // Student rates school
        await req('POST', '/school-rates', { note: 4.5, comment: "Nice school!", schoolId: schools[0].id, userId: student.id }, studentToken);
        
        // Expert rates school, program, course
        await req('POST', '/school-rates', { note: 5.0, comment: "Excellent facilities.", schoolId: schools[1].id, userId: expert.id }, expertToken);
        await req('POST', '/program-rates', { note: 4.0, comment: "Solid curriculum.", programId: programs[0].id, userId: expert.id }, expertToken);
        await req('POST', '/course-rates', { note: 3.5, comment: "Good basics.", courseId: courses[0].id, userId: expert.id }, expertToken);

        console.log("13. Fetching and Validating...");
        const searchSchools = await req('GET', '/schools/search?tag=ENGINEERING');
        console.log(`   Schools with ENGINEERING tag: ${searchSchools.totalElements}`);
        
        const s0Rates = await req('GET', `/school-rates/school/${schools[0].id}`);
        console.log(`   School 0 ratings count: ${s0Rates.length}`);

        console.log("All tests passed successfully!");

    } catch (err) {
        console.error("Test failed:", err);
        process.exit(1);
    }
}

run();
