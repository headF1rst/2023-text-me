package gifterz.textme.domain.letter.service;

import gifterz.textme.domain.letter.dto.request.LetterRequest;
import gifterz.textme.domain.letter.dto.response.AllLetterResponse;
import gifterz.textme.domain.letter.dto.response.LetterResponse;
import gifterz.textme.domain.letter.entity.Letter;
import gifterz.textme.domain.letter.exception.LetterNotFoundException;
import gifterz.textme.domain.letter.repository.LetterRepository;
import gifterz.textme.domain.security.service.AesUtils;
import gifterz.textme.domain.user.entity.User;
import gifterz.textme.domain.user.exception.UserNotFoundException;
import gifterz.textme.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;


@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LetterService {
    private final UserRepository userRepository;
    private final LetterRepository letterRepository;
    private final AesUtils aesUtils;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public LetterResponse makeLetter(LetterRequest request) {
        String decryptedId = aesUtils.decryption(request.getReceiverId());
        Long userId = Long.valueOf(decryptedId);
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Letter letter = Letter.of(user, request.getSenderName(), request.getContents(), request.getImageUrl());
        letterRepository.save(letter);
        notifyLetterSent(letter);
        return new LetterResponse(letter.getId(), user.getName(),
                request.getSenderName(), request.getContents(), request.getImageUrl());
    }

    private void notifyLetterSent(Letter letter) {
        letter.publishEvent(eventPublisher);
    }

    public List<AllLetterResponse> findLettersByUserId(Long id) {
        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);
        List<Letter> letterList = letterRepository.findAllByUserId(user.getId());
        return letterList.stream()
                .map(letter -> new AllLetterResponse(letter.getId(), user.getName(), letter.getImageUrl()))
                .collect(Collectors.toList());
    }

    public LetterResponse findLetter(String email, Long id) {
        User user = userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
        Letter letter = letterRepository.findById(id).orElseThrow(LetterNotFoundException::new);
        if (user != letter.getUser()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "편지를 볼 권한이 없습니다.");
        }
        return new LetterResponse(letter.getId(), user.getName(), letter.getSenderName(),
                letter.getContents(), letter.getImageUrl());
    }
}
